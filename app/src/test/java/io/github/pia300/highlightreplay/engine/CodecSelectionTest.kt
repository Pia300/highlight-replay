package io.github.pia300.highlightreplay.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 编码器选型与尺寸钳制的单元测试。
 *
 * 只覆盖纯决策核心 [selectEncoder]（脱离 MediaCodecList，可在 JVM 运行）。
 */
class CodecSelectionTest {

    /** 构造候选：给定支持范围与对齐步长。 */
    private fun candidate(
        name: String,
        hardware: Boolean,
        maxWidth: Int = 3840,
        maxHeight: Int = 2160,
        minWidth: Int = 128,
        minHeight: Int = 128,
        widthAlignment: Int = 16,
        heightAlignment: Int = 16
    ) = CodecCandidate(
        name = name,
        hardware = hardware,
        minWidth = minWidth,
        maxWidth = maxWidth,
        minHeight = minHeight,
        maxHeight = maxHeight,
        widthAlignment = widthAlignment,
        heightAlignment = heightAlignment
    )

    /** 能力未知的候选：所有范围字段为 null。 */
    private fun unknownCapabilityCandidate(name: String, hardware: Boolean) =
        CodecCandidate(name, hardware, null, null, null, null, null, null)

    /** 硬件偏好下选中第一个硬件候选，并返回钳制后的尺寸。 */
    @Test
    fun selectsHardwareCandidateAndClampsSize() {
        val plan = selectEncoder(
            candidates = listOf(candidate("c2.qti.hevc.encoder", hardware = true, maxWidth = 1920, maxHeight = 1080)),
            wantWidth = 2560,
            wantHeight = 1440,
            wantHardware = true
        )
        assertEquals("c2.qti.hevc.encoder", plan?.codecName)
        assertTrue("尺寸应被钳制到 ≤1920×1080", (plan?.width ?: 0) <= 1920 && (plan?.height ?: 0) <= 1080)
        assertTrue(plan?.clamped == true)
    }

    /** 软件偏好跳过硬件候选，选到软件候选。 */
    @Test
    fun softwarePreferenceSkipsHardwareCandidates() {
        val plan = selectEncoder(
            candidates = listOf(
                candidate("c2.qti.hevc.encoder", hardware = true),
                candidate("c2.android.hevc.encoder", hardware = false)
            ),
            wantWidth = 1920,
            wantHeight = 1080,
            wantHardware = false
        )
        assertEquals("c2.android.hevc.encoder", plan?.codecName)
        assertEquals(false, plan?.hardware)
    }

    /** 尺寸满足时不做缩放，但仍按对齐步长归一化（clamped 仅在数值变化时为 true）。 */
    @Test
    fun alignedSizeNeedsNoShrink() {
        val plan = selectEncoder(
            candidates = listOf(candidate("hw.encoder", hardware = true)),
            wantWidth = 1920,
            wantHeight = 1088,
            wantHardware = true
        )
        assertEquals(1920, plan?.width)
        assertEquals(1088, plan?.height)
        assertEquals(false, plan?.clamped)
    }

    /** 请求尺寸未对齐时按步长向下对齐，且标记 clamped。 */
    @Test
    fun unalignedRequestIsAlignedDown() {
        val plan = selectEncoder(
            candidates = listOf(candidate("hw.encoder", hardware = true)),
            wantWidth = 1920,
            wantHeight = 1080,
            wantHardware = true
        )
        assertEquals(1920, plan?.width)
        assertEquals("1080 向下对齐到 16 的倍数", 1072, plan?.height)
        assertEquals(true, plan?.clamped)
    }

    /** 尺寸不满足时换下一个候选：上限过小的候选即使缩到最小仍不满足则跳过。 */
    @Test
    fun skipsCandidateThatCannotSatisfySize() {
        val plan = selectEncoder(
            candidates = listOf(
                // 上限 320×320 且最小边长保护为 320：无法缩到 3840×2160 之外可接受的范围 → 跳过
                candidate(
                    "tiny.encoder", hardware = true,
                    maxWidth = 320, maxHeight = 320, minWidth = 320, minHeight = 320
                ),
                candidate("big.encoder", hardware = true, maxWidth = 3840, maxHeight = 2160)
            ),
            wantWidth = 3840,
            wantHeight = 2160,
            wantHardware = true
        )
        assertEquals("big.encoder", plan?.codecName)
    }

    /** 上限较小的候选若能通过缩小满足请求，则优先选中它（列表顺序优先）。 */
    @Test
    fun shrinksIntoSmallerCandidateWhenPossible() {
        val plan = selectEncoder(
            candidates = listOf(
                candidate("small.encoder", hardware = true, maxWidth = 640, maxHeight = 480),
                candidate("big.encoder", hardware = true, maxWidth = 3840, maxHeight = 2160)
            ),
            wantWidth = 3840,
            wantHeight = 2160,
            wantHardware = true
        )
        assertEquals("small.encoder", plan?.codecName)
        assertTrue("应缩到 ≤640×480", (plan?.width ?: 0) <= 640 && (plan?.height ?: 0) <= 480)
        assertEquals(true, plan?.clamped)
    }

    /** 能力未知的候选按原尺寸接受（无法判断，交由 configure 决定）。 */
    @Test
    fun acceptsUnknownCapabilityCandidateAtRequestedSize() {
        val plan = selectEncoder(
            candidates = listOf(unknownCapabilityCandidate("unknown.encoder", hardware = true)),
            wantWidth = 2560,
            wantHeight = 1440,
            wantHardware = true
        )
        assertEquals("unknown.encoder", plan?.codecName)
        assertEquals(2560, plan?.width)
        assertEquals(1440, plan?.height)
        assertEquals(false, plan?.clamped)
    }

    /** 无匹配候选时返回 null，由调用方回退系统代选。 */
    @Test
    fun returnsNullWhenNoCandidateMatches() {
        assertNull(
            selectEncoder(
                candidates = listOf(candidate("c2.android.hevc.encoder", hardware = false)),
                wantWidth = 1920,
                wantHeight = 1080,
                wantHardware = true
            )
        )
        assertNull(
            selectEncoder(
                candidates = emptyList(),
                wantWidth = 1920,
                wantHeight = 1080,
                wantHardware = true
            )
        )
    }

    /** 缩小结果保持对齐步长，且不小于最小边长。 */
    @Test
    fun clampedSizeKeepsAlignment() {
        val plan = selectEncoder(
            candidates = listOf(
                candidate(
                    "hw.encoder", hardware = true,
                    maxWidth = 1024, maxHeight = 1024,
                    widthAlignment = 16, heightAlignment = 16
                )
            ),
            wantWidth = 2560,
            wantHeight = 2560,
            wantHardware = true
        )
        val w = plan?.width ?: 0
        val h = plan?.height ?: 0
        assertEquals("宽度应对齐到 16 的倍数", 0, w % 16)
        assertEquals("高度应对齐到 16 的倍数", 0, h % 16)
        assertTrue("应缩到支持范围内", w <= 1024 && h <= 1024)
    }
}
