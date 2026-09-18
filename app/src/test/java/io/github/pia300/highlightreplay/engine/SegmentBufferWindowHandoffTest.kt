package io.github.pia300.highlightreplay.engine

import io.github.pia300.highlightreplay.data.MediaData
import io.github.pia300.highlightreplay.data.SegmentRingBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分片缓冲与窗口选取的**交接处**行为。
 *
 * 这两块各有单测（`SegmentRingBufferTest` / `ReplayClipSelectorTest`），但"缓冲在异常编码器下
 * 到底进了什么、选取又接不接得住"是跨两者的行为，此前没有覆盖。它决定三件用户可见的事：
 *
 * 1. 编码器**从不**发关键帧时会发生什么（结论：缓冲一直为空，与"强制切段"无关）；
 * 2. 编码器只发一次关键帧就停手时，硬上限兜底能否让保存仍然成功（结论：能，但开头要丢一段）；
 * 3. `replay_no_keyframe` 究竟何时可达（结论：窗口内一个关键帧都没有时才可达）。
 *
 * 这三个结论直接关系到"产物会不会整段无法解码"，故用断言钉住，而不是靠读代码推断。
 *
 * 注意喂帧的时间轴：帧间隔取 33ms（30fps），因此**帧时间戳是 33 的倍数**，
 * 不要用 1000/8052 这类不在该网格上的值当关键帧时刻——那样关键帧根本不会被喂进去。
 */
class SegmentBufferWindowHandoffTest {

    /** 帧间隔（毫秒）。所有时间戳都落在它的整数倍上。 */
    private val stepMs = 33L

    /** MediaCodec.BUFFER_FLAG_KEY_FRAME；单测 JVM 上该常量可正常解析（已实测 = 1）。 */
    private val keyFrameFlag = 1
    private val deltaFrameFlag = 0

    /**
     * 按固定步长喂入 `[0, untilMs]` 的帧。
     * [keyFrameOrdinals] 用**帧序号**指定关键帧，避免依赖时间戳能否被整除。
     */
    private fun feed(
        buffer: SegmentRingBuffer,
        untilMs: Long,
        keyFrameOrdinals: Set<Int> = emptySet()
    ) {
        var ordinal = 0
        var t = 0L
        while (t <= untilMs) {
            val isKey = ordinal in keyFrameOrdinals
            buffer.put(
                MediaData(
                    ByteArray(4),
                    t * 1000,
                    if (isKey) keyFrameFlag else deltaFrameFlag,
                    t
                )
            )
            ordinal++
            t += stepMs
        }
    }

    private fun newBuffer() = SegmentRingBuffer(
        capacityMs = 60_000,
        softSegmentMs = 1_600L,
        hardSegmentMs = 4_000L
    )

    /**
     * 编码器**从不**发关键帧：`openSegment` 永远打不开，所有 delta 帧在 `put()` 里被丢弃，
     * 缓冲始终为空——**硬上限不会触发**（它只作用于已打开的分片）。
     *
     * 这条纠正了一个容易想当然的判断："编码器不发关键帧时硬上限会兜底"。
     * 实际后果是缓冲为空、保存直接报 `replay_buffer_empty`，与硬上限无关；
     * 硬上限兜底的是下面第 2 条那种"先给一个关键帧、然后停手"的编码器。
     */
    @Test
    fun `从不发关键帧时缓冲保持为空且硬上限不触发`() {
        val buffer = newBuffer()
        feed(buffer, untilMs = 12_000, keyFrameOrdinals = emptySet())

        assertEquals("没有关键帧就不该打开分片", 0, buffer.segmentCount)
        assertEquals("硬上限只作用于已打开的分片，此处不应触发", 0, buffer.forcedCutCount)
        assertTrue("缓冲应为空，故保存会走 replay_buffer_empty", buffer.snapshot(60_000).isEmpty())
    }

    /**
     * 编码器只发**一个**关键帧然后停手：这是硬上限真正兜底的场景。
     * 分片被迫按硬上限切开，切出的新分片**不以关键帧开头**——这正是上层必须从关键帧起拼接的原因。
     */
    @Test
    fun `只发一个关键帧后停手时硬上限强制切段`() {
        val buffer = newBuffer()
        feed(buffer, untilMs = 12_000, keyFrameOrdinals = setOf(0))

        assertTrue("超过硬上限后应发生强制切段", buffer.forcedCutCount > 0)
        assertTrue("分片数应大于 1", buffer.segmentCount > 1)

        val firsts = buffer.snapshot(windowMs = 60_000).map { it.packets.first() }
        assertTrue("首个分片以关键帧开头", firsts.first().isKeyFrame())
        assertTrue(
            "强制切出的后续分片不以关键帧开头（故上层必须从关键帧起拼接）",
            firsts.drop(1).any { !it.isKeyFrame() }
        )
    }

    /**
     * 接上一条：迟到到达的关键帧成为选取起点，保存**不会失败**，代价是丢弃它之前的那一段。
     * 这是"产物不会整段花屏"的核心保证。
     */
    @Test
    fun `强制切段后到达的关键帧可作为选取起点`() {
        val buffer = newBuffer()
        // 硬上限 4000ms；先把 0 号帧设为关键帧，其余靠 delta 触发强制切段。
        val lateKeyOrdinal = (8_000L / stepMs).toInt()   // 恰为 8000ms 处的帧
        val lateKeyAt = lateKeyOrdinal * stepMs

        feed(buffer, untilMs = 10_000, keyFrameOrdinals = setOf(0, lateKeyOrdinal))
        assertTrue("应先发生强制切段", buffer.forcedCutCount > 0)

        val packets = buffer.snapshot(windowMs = 60_000).flatMap { it.packets }
        val keyTimes = packets.filter { it.isKeyFrame() }.map { it.timeStamp }
        assertEquals("本场景应恰有两个关键帧（0 与 $lateKeyAt），实际=$keyTimes", 2, keyTimes.size)

        // 窗口起点必须落在两个关键帧**之间**：若传 0，则"窗口内首个关键帧"就是 0 号帧，
        // 选取理所当然从 0 起头（真实调用点传的是 triggerTime - durationMs，不会是 0）。
        val targetStartTime = 5_000L
        val selection = selectFramesInWindow(
            packets, emptyList(), targetStartTime, packets.last().timeStamp
        )
        assertNotNull("窗口内有关键帧时必须能选取", selection)
        assertEquals(
            "输出必须从窗口内首个关键帧（$lateKeyAt）起头，而非窗口之前的那个",
            lateKeyAt,
            selection!!.startTimestamp
        )
        assertTrue("起头帧必须是关键帧", selection.videoFrames.first().isKeyFrame())
        assertTrue("不应保留关键帧之前的帧", selection.videoFrames.all { it.timeStamp >= lateKeyAt })
    }

    /**
     * 窗口内**确实**一个关键帧都没有时，选取返回 null —— `replay_no_keyframe` 可达。
     * 构造方式：只有 0 号帧是关键帧，窗口只覆盖它之后的那一段。
     */
    @Test
    fun `窗口内无关键帧时选取返回 null`() {
        val buffer = newBuffer()
        feed(buffer, untilMs = 12_000, keyFrameOrdinals = setOf(0))

        val packets = buffer.snapshot(windowMs = 60_000).flatMap { it.packets }
        assertEquals("本场景只应有一个关键帧", 1, packets.count { it.isKeyFrame() })

        val afterFirstKey = packets.filter { it.timeStamp > 0L }
        val selection = selectFramesInWindow(
            afterFirstKey, emptyList(), afterFirstKey.first().timeStamp, afterFirstKey.last().timeStamp
        )
        assertNull("范围内无关键帧时必须返回 null，而不是产出无法解码的产物", selection)
    }

    /** 守约编码器（每约 1 秒一个关键帧）：选取正常起头，且不发生强制切段。 */
    @Test
    fun `守约编码器下选取从窗口内首个关键帧起头`() {
        val buffer = newBuffer()
        // 每 999ms 一帧的整数倍处给关键帧（33ms × 30 ≈ 990ms，取帧序号每 30 帧一个）。
        val keyOrdinals = (0..(10_000L / stepMs).toInt() step 30).toSet()
        feed(buffer, untilMs = 10_000, keyFrameOrdinals = keyOrdinals)

        val packets = buffer.snapshot(windowMs = 3_000).flatMap { it.packets }
        val selection = selectFramesInWindow(
            packets, emptyList(), packets.first().timeStamp, packets.last().timeStamp
        )

        assertNotNull(selection)
        assertTrue("起头必须是关键帧", selection!!.videoFrames.first().isKeyFrame())
        assertEquals("守约情况下不应发生强制切段", 0, buffer.forcedCutCount)
    }
}
