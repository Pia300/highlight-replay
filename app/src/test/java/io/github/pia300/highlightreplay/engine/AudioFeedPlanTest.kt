package io.github.pia300.highlightreplay.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 音频块入队判定的单元测试。
 *
 * 背景：`AudioEncoder.feedEncoder` 原先直接 `buf.put(data, 0, size)`，未校验输入槽容量。
 * 槽小于整块时抛 `BufferOverflowException` 被外层 catch 吞掉，该块既不入队、
 * 也不计入 `droppedInputBlocks`、时间轴也不前进——违背该函数 KDoc 的承诺。
 */
class AudioFeedPlanTest {

    /** 槽与块等大：可整块写入。 */
    @Test
    fun exactFitIsAccepted() {
        assertTrue(audioBlockFitsInputSlot(slotBytes = 4096, blockSize = 4096))
    }

    /** 槽富余：可整块写入。 */
    @Test
    fun largerSlotIsAccepted() {
        assertTrue(audioBlockFitsInputSlot(slotBytes = 8192, blockSize = 4096))
    }

    /** 槽比块小一字节：装不下，必须走丢块路径。 */
    @Test
    fun oneByteShortIsRejected() {
        assertFalse(audioBlockFitsInputSlot(slotBytes = 4095, blockSize = 4096))
    }

    /** 槽远小于块：装不下。 */
    @Test
    fun muchSmallerSlotIsRejected() {
        assertFalse(audioBlockFitsInputSlot(slotBytes = 1024, blockSize = 4096))
    }

    /** 槽为 0：装不下，且不得被当作"可写"。 */
    @Test
    fun emptySlotIsRejected() {
        assertFalse(audioBlockFitsInputSlot(slotBytes = 0, blockSize = 4096))
    }

    /** 待写长度为 0：形式上可写入（边界，不应被误判为溢出）。 */
    @Test
    fun zeroBlockAlwaysFits() {
        assertTrue(audioBlockFitsInputSlot(slotBytes = 0, blockSize = 0))
    }

    /**
     * 判定与实际写入长度必须一致：[AudioEncoder] 用 `minOf(size, remaining)` 记日志、
     * 用本判定分支，两者对"是否装得下"必须给出同一结论。
     */
    @Test
    fun decisionMatchesMinOf() {
        val cases = listOf(0 to 0, 0 to 1, 100 to 4096, 4096 to 4096, 4097 to 4096, 8192 to 4096)
        for ((slot, block) in cases) {
            val writable = minOf(block, slot)
            assertEquals(
                "slot=$slot block=$block 时判定与 minOf 结论不一致",
                writable == block,
                audioBlockFitsInputSlot(slot, block)
            )
        }
    }
}
