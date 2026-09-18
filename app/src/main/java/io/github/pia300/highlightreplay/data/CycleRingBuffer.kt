package io.github.pia300.highlightreplay.data

/**
 * 线程安全的固定容量循环缓冲；写满覆盖最旧数据，并可按字节预算淘汰最旧元素。
 *
 * **当前只服务音频**（`ScreenRecorder.audioBuffer`，元素为音频帧 [MediaData]）。
 * 视频自 `2362593` 起改用 [SegmentRingBuffer]——因为回放要能独立解码，
 * 而"关键帧对齐的分片边界"这个不变量必须在**写入时**建立，事后按帧搜索补不回来。
 * 音频不需要该不变量（PCM 块无关键帧概念），故仍用本类：容量按块数表达、逐块固定大小。
 *
 * 若再次出现"视频用哪个缓冲"的疑问，看 `ScreenRecorder` 里两个缓冲的构造点即可。
 */
class CycleRingBuffer<T : Any>(
    capacity: Int,
    // 字节预算上限，超限时淘汰最旧元素。
    private val maxBytes: Long = Long.MAX_VALUE,
    // 计算单个元素字节数的回调，用于字节预算淘汰。
    private val sizeOf: (T) -> Int = { 0 }
) {

    init {

        require(capacity > 0) { "capacity must be > 0, got $capacity" }
    }

    private val bufferSize: Int = capacity

    // 环形底层存储；null 区分空槽位与已填充槽位。
    @Suppress("UNCHECKED_CAST")
    private val ringBuffer: Array<T?> = arrayOfNulls<Any?>(bufferSize) as Array<T?>

    private var writeIndex = 0
    private var size = 0

    // 最旧元素索引，供快照与超预算淘汰使用。
    private var startIndex = 0

    // 当前所有元素的估算总字节数，用于 maxBytes 预算淘汰。
    private var totalBytes = 0L

    /** 清空缓冲区，重置索引与字节计数。 */
    @Synchronized
    fun clear() {
        ringBuffer.fill(null)
        writeIndex = 0
        size = 0
        startIndex = 0
        totalBytes = 0L
    }

    /** 写入一条数据；满时覆盖最旧，超预算时再淘汰最旧元素。 */
    @Synchronized
    fun put(data: T) {

        // 元素大小钳为非负，避免字节计数异常。
        val dataSize = sizeOf(data).coerceAtLeast(0)

        // floorMod 保证回绕索引非负；writeIndex 自身也保持归一化，
        // 避免长期自增到 Int 溢出后（约 2^31 次写入）破坏 startIndex + size == writeIndex 不变量。
        val index = writeIndex
        writeIndex = Math.floorMod(writeIndex + 1, bufferSize)
        val overwritten = ringBuffer[index]
        if (size == 0) {

            startIndex = index
        }
        // 覆盖旧元素：回收其字节计数并前移最旧位置。
        if (overwritten != null) {

            totalBytes -= sizeOf(overwritten).coerceAtLeast(0)
            startIndex = Math.floorMod(startIndex + 1, bufferSize)
        }
        ringBuffer[index] = data
        totalBytes += dataSize
        if (size < bufferSize) {
            // 未满才递增；已满保持满（覆盖语义）。
            size++
        }
        evictIfOverBudget()
    }

    /** 超预算时循环淘汰最旧元素，至少保留一个。 */
    private fun evictIfOverBudget() {
        // 槽位意外为空即停止，防止无限循环。
        while (totalBytes > maxBytes && size > 1) {
            val oldest = ringBuffer[startIndex] ?: break
            ringBuffer[startIndex] = null
            totalBytes -= sizeOf(oldest).coerceAtLeast(0)
            size--
            startIndex = Math.floorMod(startIndex + 1, bufferSize)
        }
    }

    /**
     * 返回从旧到新的全部元素快照。
     * 仅在锁内做一次 arraycopy，列表构建放到锁外：保存回放时元素可达数万，
     * 持锁做 ArrayList 扩容会阻塞编码线程的 put()。
     */
    fun snapshot(): List<T> {
        val slots = copySlots()
        if (slots.isEmpty()) return emptyList()
        val result = ArrayList<T>(slots.size)
        for (i in slots.indices) {
            result.add(
                slots[i] ?: error("ring buffer invariant broken: null slot at $i of ${slots.size}")
            )
        }
        return result
    }

    /** 在锁内按从旧到新的顺序拷贝槽位（两段 arraycopy 覆盖回绕）。 */
    @Synchronized
    private fun copySlots(): Array<T?> {
        val currentSize = size
        if (currentSize == 0) {
            @Suppress("UNCHECKED_CAST")
            return arrayOfNulls<Any?>(0) as Array<T?>
        }
        @Suppress("UNCHECKED_CAST")
        val out = arrayOfNulls<Any?>(currentSize) as Array<T?>
        val firstPart = minOf(currentSize, bufferSize - startIndex)
        System.arraycopy(ringBuffer, startIndex, out, 0, firstPart)
        if (currentSize > firstPart) {
            System.arraycopy(ringBuffer, 0, out, firstPart, currentSize - firstPart)
        }
        return out
    }
}
