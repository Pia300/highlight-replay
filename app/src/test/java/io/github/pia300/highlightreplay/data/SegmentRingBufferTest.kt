package io.github.pia300.highlightreplay.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分片缓冲的单元测试。
 *
 * 重点覆盖三条保护：
 * 1. 分片边界即关键帧边界 —— 快照可直接拼接；
 * 2. 软上限触发关键帧请求回调，且按节流去重；
 * 3. 硬上限使单分片长度与内存占用有界（编码器始终不补发关键帧时）。
 */
class SegmentRingBufferTest {

    private val keyFrame = 1
    private val deltaFrame = 0

    /** 构造一个采样；帧数据长度可指定，用于字节预算断言。 */
    private fun frame(timeStamp: Long, flags: Int = deltaFrame, size: Int = 100) =
        MediaData(ByteArray(size), timeStamp * 1000, flags, timeStamp)

    /**
     * 按固定帧率喂入数据。[keyFrameEveryMs] 为 0 表示只在开头给一个关键帧（模拟不守约的编码器）。
     *
     * 时间戳由帧序号换算为整数毫秒，避免浮点累加误差影响关键帧判定的整除关系。
     */
    private fun feed(
        buffer: SegmentRingBuffer,
        durationMs: Long,
        fps: Int = 30,
        keyFrameEveryMs: Long = 1000,
        startMs: Long = 0
    ) {
        val totalFrames = (durationMs * fps / 1000).toInt()
        val framesPerKeyFrame = if (keyFrameEveryMs > 0) (keyFrameEveryMs * fps / 1000).toInt() else 0
        for (i in 0 until totalFrames) {
            val t = startMs + i * 1000L / fps
            val isKey = i == 0 || (framesPerKeyFrame > 0 && i % framesPerKeyFrame == 0)
            buffer.put(frame(t, if (isKey) keyFrame else deltaFrame))
        }
    }

    // ---- 1. 分片边界即关键帧边界 ----

    /** 首个包非关键帧时被丢弃，直到遇到首个 IDR 才开始记录。 */
    @Test
    fun `首个包之前无分片，遇到关键帧后才开始记录`() {
        val buffer = SegmentRingBuffer(capacityMs = 10_000)
        buffer.put(frame(0, deltaFrame))
        buffer.put(frame(33, deltaFrame))
        assertEquals(0, buffer.segmentCount)

        buffer.put(frame(66, keyFrame))
        assertEquals(1, buffer.segmentCount)
    }

    /** 快照覆盖窗口，且首个分片以关键帧开头。 */
    @Test
    fun `快照覆盖窗口且首分片以关键帧开头`() {
        val buffer = SegmentRingBuffer(capacityMs = 30_000)
        feed(buffer, durationMs = 20_000, keyFrameEveryMs = 1000)

        val snapshot = buffer.snapshot(windowMs = 5_000)

        assertTrue("快照不应为空", snapshot.isNotEmpty())
        assertTrue("首分片首包应为关键帧", snapshot.first().packets.first().isKeyFrame())
        val covered = snapshot.last().endTimeMs - snapshot.first().startTimeMs
        assertTrue("覆盖 $covered 应 >= 窗口", covered >= 5_000 - 40)
        assertTrue("覆盖 $covered 不应远超窗口", covered < 7_500)
    }

    /** 每个分片的首包都是关键帧（编码器守约时）。 */
    @Test
    fun `守约编码器下每个分片均以关键帧开头`() {
        val buffer = SegmentRingBuffer(capacityMs = 30_000)
        feed(buffer, durationMs = 10_000, keyFrameEveryMs = 1000)

        val snapshot = buffer.snapshot(windowMs = 30_000)
        assertTrue(snapshot.size >= 5)
        snapshot.forEach { segment ->
            assertTrue(
                "分片 @${segment.startTimeMs} 的首包应为关键帧",
                segment.packets.first().isKeyFrame()
            )
        }
    }

    /** 超出容量后最旧分片被逐出，且至少保留一个已封闭分片。 */
    @Test
    fun `超出容量后最旧分片被逐出`() {
        val buffer = SegmentRingBuffer(capacityMs = 5_000)
        feed(buffer, durationMs = 30_000, keyFrameEveryMs = 1000)

        // 跨度应 <= 容量 + 一个分片的余量
        assertTrue("跨度 ${buffer.bufferedDurationMs}", buffer.bufferedDurationMs <= 7_000)
        assertTrue("分片数 ${buffer.segmentCount}", buffer.segmentCount in 2..8)
    }

    // ---- 2. 软上限：请求关键帧回调 ----

    /** 编码器不发关键帧时，开放分片超过软上限即触发回调。 */
    @Test
    fun `软上限触发关键帧请求回调`() {
        val buffer = SegmentRingBuffer(
            capacityMs = 60_000, softSegmentMs = 1600, hardSegmentMs = 4000,
            overrunThrottleMs = 1000
        )
        var overruns = 0
        buffer.onSegmentOverrun = { overruns++ }

        // 只给一个开头关键帧，之后全是普通帧
        feed(buffer, durationMs = 6_000, keyFrameEveryMs = 0)

        assertTrue("应触发超时回调，实际 $overruns", overruns >= 1)
    }

    /** 回调按节流间隔去重：软上限逐帧求值，但不应逐帧回调。 */
    @Test
    fun `请求回调按节流间隔去重`() {
        val throttleMs = 1000L
        val buffer = SegmentRingBuffer(
            capacityMs = 60_000, softSegmentMs = 1600, hardSegmentMs = 100_000,
            overrunThrottleMs = throttleMs
        )
        var overruns = 0
        buffer.onSegmentOverrun = { overruns++ }

        // 20 秒无关键帧、30fps = 600 帧，若不去重会接近 600 次
        feed(buffer, durationMs = 20_000, keyFrameEveryMs = 0)

        assertTrue("回调次数 $overruns 应远小于帧数", overruns <= 25)
        assertTrue("但仍应周期性触发", overruns >= 5)
    }

    // ---- 3. 硬上限：分片长度的显式上界 ----

    /** 编码器始终不发关键帧时，单分片长度不超过硬上限。 */
    @Test
    fun `硬上限约束单个分片长度`() {
        val hardMs = 4000L
        val buffer = SegmentRingBuffer(
            capacityMs = 60_000, softSegmentMs = 1600, hardSegmentMs = hardMs,
            overrunThrottleMs = 1000
        )
        feed(buffer, durationMs = 30_000, keyFrameEveryMs = 0)

        val snapshot = buffer.snapshot(windowMs = 60_000)
        assertTrue(snapshot.size >= 5)
        snapshot.forEach { segment ->
            assertTrue(
                "分片 @${segment.startTimeMs} 长度 ${segment.endTimeMs - segment.startTimeMs} " +
                        "不应超过硬上限 $hardMs",
                segment.endTimeMs - segment.startTimeMs <= hardMs + 100
            )
        }
        assertTrue("应记录到强制切段", buffer.forcedCutCount >= 5)
    }

    /** 硬上限切段后，快照仍可从首个关键帧起拼出可解码序列。 */
    @Test
    fun `硬上限切段后仍可从首个关键帧起拼接`() {
        val buffer = SegmentRingBuffer(
            capacityMs = 60_000, softSegmentMs = 1600, hardSegmentMs = 4000,
            overrunThrottleMs = 1000
        )
        // 开头一个关键帧，之后全程无关键帧
        buffer.put(frame(0, keyFrame))
        feed(buffer, durationMs = 10_000, keyFrameEveryMs = 0, startMs = 40)

        val snapshot = buffer.snapshot(windowMs = 60_000)
        val allFrames = snapshot.flatMap { it.packets }

        // 保存侧的安全网：从首个关键帧起拼接
        val firstKeyIndex = allFrames.indexOfFirst { it.isKeyFrame() }
        assertTrue("序列中应存在关键帧", firstKeyIndex >= 0)
        val usable = allFrames.subList(firstKeyIndex, allFrames.size)
        assertTrue("可拼接序列不应为空", usable.isNotEmpty())
        assertTrue("可拼接序列首帧应为关键帧", usable.first().isKeyFrame())
    }

    /** 编码器始终不发关键帧时，总字节数仍被字节预算约束。 */
    @Test
    fun `硬上限与字节预算共同约束内存`() {
        // 每帧 100 字节、30fps、容量 5 秒 => 理想上限约 5s * 30 * 100 = 15000 字节
        val buffer = SegmentRingBuffer(
            capacityMs = 5_000, maxBytes = 20_000,
            softSegmentMs = 1600, hardSegmentMs = 4000, overrunThrottleMs = 1000
        )
        feed(buffer, durationMs = 60_000, keyFrameEveryMs = 0)

        assertTrue(
            "字节数 ${buffer.bufferedBytes} 应被预算约束",
            buffer.bufferedBytes <= 20_000 + 4_000 / 33 * 100 + 200
        )
        assertTrue("跨度 ${buffer.bufferedDurationMs}", buffer.bufferedDurationMs <= 9_000)
    }

    // ---- 其他契约 ----

    /** clear 后索引与字节计数复位，可继续写入。 */
    @Test
    fun `clear 复位后可继续写入`() {
        val buffer = SegmentRingBuffer(capacityMs = 10_000)
        feed(buffer, durationMs = 5_000)
        buffer.clear()
        assertEquals(0, buffer.segmentCount)
        assertEquals(0L, buffer.bufferedBytes)

        buffer.put(frame(0, keyFrame))
        assertNotNull(buffer.snapshot(1_000).firstOrNull())
    }

    /** 缓冲为空时快照为空。 */
    @Test
    fun `空缓冲快照为空`() {
        val buffer = SegmentRingBuffer(capacityMs = 10_000)
        assertTrue(buffer.snapshot(1_000).isEmpty())
    }

    /** 硬上限必须大于软上限，否则配置无意义。 */
    @Test
    fun `硬上限必须大于软上限`() {
        val error = runCatching {
            SegmentRingBuffer(capacityMs = 10_000, softSegmentMs = 4000, hardSegmentMs = 1600)
        }.exceptionOrNull()
        assertTrue("应拒绝非法阈值", error is IllegalArgumentException)
    }

    /** 容量必须为正。 */
    @Test
    fun `容量必须为正`() {
        val error = runCatching { SegmentRingBuffer(capacityMs = 0) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }
}
