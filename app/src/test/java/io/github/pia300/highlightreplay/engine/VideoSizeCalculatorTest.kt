package io.github.pia300.highlightreplay.engine

import io.github.pia300.highlightreplay.data.CaptureOrientation
import io.github.pia300.highlightreplay.data.ResolutionMode
import org.junit.Assert.assertEquals
import org.junit.Test

/** VideoSizeCalculator 的单元测试：覆盖自适应与标准模式在不同屏幕尺寸、分辨率和方向下的输出。 */
class VideoSizeCalculatorTest {

    /** 竖屏自适应下短边目标 1080 向下 16 对齐为 1072，长边 2400 不变。 */
    @Test
    fun adaptivePortraitScreenKeepsShortSide() {

        assertEquals(1072 to 2400, compute(1080, 2400, 1080, ADAPTIVE, AUTO))
    }

    /** 验证竖屏屏幕强制横屏时以短边为锚点，返回横向尺寸。 */
    @Test
    fun adaptiveLandscapeForcedOnPortraitScreenAnchorsShortSide() {

        assertEquals(2400 to 1072, compute(1080, 2400, 1080, ADAPTIVE, LANDSCAPE))
    }

    /** 验证横屏屏幕强制竖屏时以短边为锚点，返回竖屏尺寸。 */
    @Test
    fun adaptivePortraitForcedOnLandscapeScreenAnchorsShortSide() {
        assertEquals(1072 to 2400, compute(3200, 1440, 1080, ADAPTIVE, PORTRAIT))
    }

    /** 验证目标分辨率超过屏幕时自适应模式钳制到屏幕尺寸。 */
    @Test
    fun adaptiveClampsToScreenWhenResolutionExceeds() {

        assertEquals(720 to 1600, compute(720, 1600, 1080, ADAPTIVE, AUTO))
        assertEquals(1600 to 720, compute(720, 1600, 1080, ADAPTIVE, LANDSCAPE))
    }

    /** 验证横屏屏幕按目标分辨率等比缩小并保持 16 对齐。 */
    @Test
    fun adaptiveDownscalesLandscapeScreen() {

        assertEquals(2400 to 1072, compute(3200, 1440, 1080, ADAPTIVE, AUTO))
    }

    /** 验证标准模式输出固定的 16:9 分辨率档位（720p/1080p/1440p）。 */
    @Test
    fun standardUsesFixed169Resolutions() {
        assertEquals(1280 to 720, compute(3200, 1440, 720, STANDARD, AUTO))
        assertEquals(1920 to 1080, compute(3200, 1440, 1080, STANDARD, AUTO))
        assertEquals(2560 to 1440, compute(3200, 1440, 1440, STANDARD, AUTO))
    }

    /** 验证标准模式跟随方向设置，AUTO 时按屏幕宽高判断横竖屏。 */
    @Test
    fun standardFollowsOrientation() {

        assertEquals(1080 to 1920, compute(3200, 1440, 1080, STANDARD, PORTRAIT))

        assertEquals(1920 to 1080, compute(1080, 2400, 1080, STANDARD, LANDSCAPE))

        assertEquals(1080 to 1920, compute(1080, 2400, 1080, STANDARD, AUTO))
        assertEquals(1920 to 1080, compute(2400, 1080, 1080, STANDARD, AUTO))
    }

    /** 验证目标分辨率超过屏幕时标准模式钳制到屏幕较短边。 */
    @Test
    fun standardClampsWhenResolutionExceedsScreen() {

        assertEquals(1920 to 1080, compute(2400, 1080, 1440, STANDARD, AUTO))
    }

    /** 验证正方形屏幕在 AUTO 方向下保持正方形输出。 */
    @Test
    fun adaptiveSquareScreenAuto() {

        val result = compute(1000, 1000, 1080, ADAPTIVE, AUTO)

        assertEquals(992 to 992, result)
    }

    /** 验证过小尺寸向下取整对齐到 16 的倍数，最小值为 16。 */
    @Test
    fun adaptiveSixteenAlignFloor() {

        assertEquals(16 to 16, compute(10, 10, 1080, ADAPTIVE, AUTO))
        assertEquals(16 to 16, compute(10, 20, 1080, ADAPTIVE, AUTO))
    }

    /** 验证极小屏幕上 ADAPTIVE 钳制到 16×16，STANDARD 钳制到 16×28 或 28×16。 */
    @Test
    fun tinyScreenClampsToMinimumInBothModes() {
        assertEquals(16 to 16, compute(10, 10, 1080, ADAPTIVE, AUTO))
        assertEquals(16 to 28, compute(10, 10, 1080, STANDARD, AUTO))
        assertEquals(28 to 16, compute(10, 10, 1080, STANDARD, LANDSCAPE))
        assertEquals(16 to 28, compute(10, 10, 1080, STANDARD, PORTRAIT))
    }

    /** 验证目标低于屏幕短边时 ADAPTIVE 等比缩小、STANDARD 用目标档位，均不放大。 */
    @Test
    fun resolutionBelowScreenShortSideScalesDownInBothModes() {
        assertEquals(1600 to 720, compute(2400, 1080, 720, ADAPTIVE, AUTO))
        assertEquals(1280 to 720, compute(2400, 1080, 720, STANDARD, AUTO))
    }

    /** 验证目标高于屏幕短边时 ADAPTIVE 不放大并对齐短边，STANDARD 以短边生成 1080p。 */
    @Test
    fun resolutionAboveScreenShortSideClampsToShortSide() {
        assertEquals(1072 to 2400, compute(1080, 2400, 1440, ADAPTIVE, AUTO))
        assertEquals(1080 to 1920, compute(1080, 2400, 1440, STANDARD, AUTO))
    }

    /** 验证正方形屏幕上 ADAPTIVE 恒为正方形（992），STANDARD 依方向输出 1000×1778 或 1778×1000。 */
    @Test
    fun squareScreenOrientationCombinations() {
        assertEquals(992 to 992, compute(1000, 1000, 1080, ADAPTIVE, AUTO))
        assertEquals(992 to 992, compute(1000, 1000, 1080, ADAPTIVE, LANDSCAPE))
        assertEquals(992 to 992, compute(1000, 1000, 1080, ADAPTIVE, PORTRAIT))

        assertEquals(1000 to 1778, compute(1000, 1000, 1080, STANDARD, AUTO))
        assertEquals(1778 to 1000, compute(1000, 1000, 1080, STANDARD, LANDSCAPE))
        assertEquals(1000 to 1778, compute(1000, 1000, 1080, STANDARD, PORTRAIT))
    }

    /** 验证 alignDown 向下取整到对齐倍数；非法对齐值原样返回（供编码器能力钳制复用）。 */
    @Test
    fun alignDownRoundsDownAndToleratesInvalidAlignment() {
        // 常规对齐：向下取整到倍数。
        assertEquals(1072, VideoSizeCalculator.alignDown(1080, 16))
        assertEquals(1920, VideoSizeCalculator.alignDown(1927, 16))
        assertEquals(2560, VideoSizeCalculator.alignDown(2560, 16))
        // 小于对齐步长时取 0（调用方负责 coerceAtLeast）。
        assertEquals(0, VideoSizeCalculator.alignDown(8, 16))
        // 对齐步长非法时原样返回，避免除零。
        assertEquals(1080, VideoSizeCalculator.alignDown(1080, 0))
        assertEquals(1080, VideoSizeCalculator.alignDown(1080, -16))
        assertEquals(1080, VideoSizeCalculator.alignDown(1080, 1))
    }

    /** 验证 STANDARD 模式在 1440p 档位输出 2560×1440（钳制前的原始请求值）。 */
    @Test
    fun standard1440pRequestsFullLongEdge() {
        assertEquals(2560 to 1440, compute(3200, 1440, 1440, STANDARD, AUTO))
        assertEquals(1440 to 2560, compute(3200, 1440, 1440, STANDARD, PORTRAIT))
    }

    private fun compute(
        screenWidth: Int,
        screenHeight: Int,
        resolution: Int,
        mode: ResolutionMode,
        orientation: CaptureOrientation
    ): Pair<Int, Int> =
        VideoSizeCalculator.compute(screenWidth, screenHeight, resolution, mode, orientation)

    private companion object {
        val ADAPTIVE = ResolutionMode.ADAPTIVE
        val STANDARD = ResolutionMode.STANDARD
        val AUTO = CaptureOrientation.AUTO
        val PORTRAIT = CaptureOrientation.PORTRAIT
        val LANDSCAPE = CaptureOrientation.LANDSCAPE
    }
}
