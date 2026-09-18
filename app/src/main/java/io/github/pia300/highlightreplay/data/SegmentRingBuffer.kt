package io.github.pia300.highlightreplay.data

/*
 * 分片缓冲的设计参考自 highlight-recorder 的 RingSegmentBuffer：
 *   https://github.com/owen88ob/highlight-recorder （GPL-3.0，作者 owen88ob）
 * 该设计把"从环形缓冲切出可解码片段"从保存时的搜索问题变为采集时的不变量
 * （分片边界即关键帧边界），并给出软/硬上限与强制切段的处理方式。
 *
 * 本文件为独立重写，未复制其源码：时间基准改用本项目的毫秒单调时钟
 * （MediaData.timeStamp），并新增字节预算逐出以免码率突增导致内存失控。
 */

/**
 * 一段以关键帧开头、到下一个关键帧之前的连续编码数据。
 *
 * 关键帧边界是分片的唯一边界来源：任何拼接都必须从分片边界开始，否则解码会花屏。
 */
class VideoSegment(val startTimeMs: Long) {

    private val packetsInternal = ArrayList<MediaData>(64)

    /** 段内全部包，按写入顺序；正常情况下首包为关键帧。 */
    val packets: List<MediaData> get() = packetsInternal

    /** 段内最后一个包的时间戳（毫秒，单调时钟）。 */
    var endTimeMs: Long = startTimeMs
        private set

    /** 段内所有包的总字节数。 */
    val sizeBytes: Int get() = packetsInternal.sumOf { it.data.size }

    fun append(packet: MediaData) {
        packetsInternal.add(packet)
        if (packet.timeStamp > endTimeMs) endTimeMs = packet.timeStamp
    }
}

/**
 * 按关键帧切分的视频环形缓冲。
 *
 * 与平铺帧环的区别在于**写入时即建立可解码边界**：分片边界由实际到达的关键帧决定，
 * 不假设 `MediaFormat.KEY_I_FRAME_INTERVAL` 一定生效。
 *
 * 快照的首个分片正常情况下以关键帧开头，但强制切段（编码器始终不补发关键帧）会产生
 * 不以关键帧开头的分片，故调用方仍需在摊平后的帧序列里按 `isKeyFrame()` 选定拼接起点；
 * 分片边界省掉的是向窗口之前**回退搜索**关键帧，不是搜索本身。
 *
 * 分片长度受两个上限约束：
 * - [softSegmentMs]：开放分片超过该长度未见关键帧时回调 [onSegmentOverrun]，
 *   由上层请求编码器补发关键帧；
 * - [hardSegmentMs]：编码器始终不响应时强制切段，使单分片长度与内存占用有界。
 *
 * 编码器不响应请求时，强制切段会产生不以关键帧开头的分片；调用方需从首个关键帧起拼接。
 *
 * 线程安全：所有公共方法加锁，编码器回调线程与保存线程可并发访问。
 *
 * @param capacityMs 缓冲覆盖时长上限（毫秒）。
 * @param maxBytes 字节预算上限，超限时按分片逐出最旧数据（防码率突增导致内存失控）。
 * @param softSegmentMs 请求关键帧的软上限（毫秒）。
 * @param hardSegmentMs 强制切段的硬上限（毫秒）。
 * @param overrunThrottleMs 软上限回调的最小间隔（毫秒），避免逐帧重复请求。
 */
class SegmentRingBuffer(
    private val capacityMs: Long,
    private val maxBytes: Long = Long.MAX_VALUE,
    private val softSegmentMs: Long = 1600L,
    private val hardSegmentMs: Long = 4000L,
    private val overrunThrottleMs: Long = 1000L
) {

    init {
        require(capacityMs > 0) { "capacityMs must be > 0, got $capacityMs" }
        require(softSegmentMs > 0) { "softSegmentMs must be > 0, got $softSegmentMs" }
        require(hardSegmentMs > softSegmentMs) {
            "hardSegmentMs ($hardSegmentMs) must exceed softSegmentMs ($softSegmentMs)"
        }
    }

    /** 开放分片超过 [softSegmentMs] 未见关键帧时回调，供上层请求编码器补发关键帧。 */
    var onSegmentOverrun: (() -> Unit)? = null

    private val lock = Any()
    private val segments = ArrayDeque<VideoSegment>()

    // 正在写入的分片；null 表示尚未见到首个关键帧。
    private var openSegment: VideoSegment? = null
    private var totalBytes = 0L

    // 上次触发软上限回调的时间戳，用于节流。
    private var lastOverrunNotifyMs = 0L

    /** 最近一次强制切段之后的累计次数，仅诊断用。 */
    @Volatile
    var forcedCutCount: Int = 0
        private set

    /** 当前缓冲覆盖时长（毫秒）。 */
    val bufferedDurationMs: Long
        get() = synchronized(lock) {
            val first = segments.firstOrNull() ?: openSegment ?: return@synchronized 0L
            val last = openSegment ?: segments.lastOrNull() ?: return@synchronized 0L
            (last.endTimeMs - first.startTimeMs).coerceAtLeast(0L)
        }

    val bufferedBytes: Long get() = synchronized(lock) { totalBytes }

    val segmentCount: Int get() = synchronized(lock) { segments.size + if (openSegment != null) 1 else 0 }

    /** 写入一个编码包，必要时开新分片、请求关键帧或强制切段。 */
    fun put(packet: MediaData) {
        var overrun = false
        synchronized(lock) {
            if (packet.isKeyFrame()) {
                openSegment?.let { segments.addLast(it) }
                openSegment = VideoSegment(packet.timeStamp)
                evictLocked()
            } else if (openSegment == null) {
                // 尚未见到首个关键帧：丢弃无法独立解码的头部。
                return
            } else {
                val seg = openSegment!!
                val span = packet.timeStamp - seg.startTimeMs
                if (span > hardSegmentMs) {
                    // 编码器始终不发关键帧：强制切段兜底，否则单分片无限增长。
                    segments.addLast(seg)
                    openSegment = VideoSegment(packet.timeStamp)
                    forcedCutCount++
                    evictLocked()
                } else if (span > softSegmentMs &&
                    packet.timeStamp - lastOverrunNotifyMs >= overrunThrottleMs
                ) {
                    lastOverrunNotifyMs = packet.timeStamp
                    overrun = true
                }
            }
            val seg = openSegment!!
            seg.append(packet)
            totalBytes += packet.data.size
        }
        // 回调在锁外触发：上层可能同步调用编码器 setParameters，不宜持锁。
        if (overrun) onSegmentOverrun?.invoke()
    }

    /**
     * 取覆盖最近 [windowMs] 毫秒的分片快照（含正在写入的开放分片），按时间升序。
     *
     * 起点选择：取结束时间不早于 `最新时间 - windowMs` 的首个分片，
     * 因此返回内容的起点可能略早于窗口（最多早一个分片），首个分片正常情况下以关键帧开头。
     */
    fun snapshot(windowMs: Long): List<VideoSegment> = synchronized(lock) {
        val all = ArrayList<VideoSegment>(segments)
        openSegment?.takeIf { it.packets.isNotEmpty() }?.let { all.add(it) }
        if (all.isEmpty()) return@synchronized emptyList()

        val newestEnd = all.last().endTimeMs
        val cutoff = newestEnd - windowMs
        val index = all.indexOfFirst { it.endTimeMs >= cutoff }
        if (index <= 0) all else ArrayList(all.subList(index, all.size))
    }

    fun clear() = synchronized(lock) {
        segments.clear()
        openSegment = null
        totalBytes = 0L
        lastOverrunNotifyMs = 0L
        forcedCutCount = 0
    }

    /** 按覆盖时长与字节预算逐出最旧分片；至少保留一个已封闭分片。 */
    private fun evictLocked() {
        while (segments.size > 1) {
            val first = segments.firstOrNull() ?: break
            val newestEnd = openSegment?.startTimeMs ?: segments.lastOrNull()?.endTimeMs ?: break
            val overTime = newestEnd - first.startTimeMs > capacityMs
            val overBytes = totalBytes > maxBytes
            if (!overTime && !overBytes) break
            segments.removeFirst()
            totalBytes -= first.sizeBytes
        }
    }
}
