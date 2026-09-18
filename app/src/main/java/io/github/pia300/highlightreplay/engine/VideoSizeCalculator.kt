package io.github.pia300.highlightreplay.engine

import io.github.pia300.highlightreplay.data.CaptureOrientation
import io.github.pia300.highlightreplay.data.ResolutionMode
import kotlin.math.roundToInt

/** 由屏幕尺寸、用户分辨率与捕获方向计算最终视频输出尺寸。 */
object VideoSizeCalculator {

    // 自适应模式的输出对齐步长：缩放结果向下对齐到该倍数（如 1080p 档 → 短边 1072，长边按屏幕宽高比等比缩放）。
    private const val OUTPUT_ALIGNMENT = 16

    // 输出边最小像素数，防极小（或 0/负）屏幕产生非法尺寸；数值与 OUTPUT_ALIGNMENT 相同但语义不同。
    private const val MIN_DIMENSION = OUTPUT_ALIGNMENT

    /** 计算输出宽高，按分辨率模式分派到对应策略。 */
    fun compute(
        screenWidth: Int,
        screenHeight: Int,
        resolution: Int,
        mode: ResolutionMode,
        orientation: CaptureOrientation
    ): Pair<Int, Int> = when (mode) {
        ResolutionMode.ADAPTIVE -> adaptive(screenWidth, screenHeight, resolution, orientation)
        ResolutionMode.STANDARD -> standard(screenWidth, screenHeight, resolution, orientation)
    }

    /**
     * 自适应模式：短边 ≤ 目标档位（长边按屏幕宽高比等比缩放，可能大于档位数值），
     * 两边均向下对齐到 16 的倍数且不小于最小尺寸。
     */
    private fun adaptive(
        screenWidth: Int,
        screenHeight: Int,
        resolution: Int,
        orientation: CaptureOrientation
    ): Pair<Int, Int> {
        val shortSide = minOf(screenWidth, screenHeight)
        val scale = minOf(1f, resolution.toFloat() / shortSide)
        // 按 OUTPUT_ALIGNMENT 向下取整对齐，输出不超过目标分辨率。
        var width = ((screenWidth * scale).toInt() / OUTPUT_ALIGNMENT) * OUTPUT_ALIGNMENT
        var height = ((screenHeight * scale).toInt() / OUTPUT_ALIGNMENT) * OUTPUT_ALIGNMENT
        // 钳到最小尺寸，防极小屏幕（取整为 0）产生非法尺寸。
        width = width.coerceAtLeast(MIN_DIMENSION)
        height = height.coerceAtLeast(MIN_DIMENSION)
        return orient(width, height, orientation)
    }

    /** 标准模式：以短边为基准按 16:9 计算输出尺寸。 */
    private fun standard(
        screenWidth: Int,
        screenHeight: Int,
        resolution: Int,
        orientation: CaptureOrientation
    ): Pair<Int, Int> {

        var short = minOf(resolution, minOf(screenWidth, screenHeight)).coerceAtLeast(MIN_DIMENSION)
        // 宽高须为偶数（编码器要求），奇数减 1 取偶；主要防御屏幕短边这类任意输入。
        if (short % 2 != 0) short--
        var long = (short * 16f / 9f).roundToInt()
        if (long % 2 != 0) long--
        val landscape = when (orientation) {
            CaptureOrientation.LANDSCAPE -> true
            CaptureOrientation.PORTRAIT -> false
            CaptureOrientation.AUTO -> screenWidth > screenHeight
        }
        return if (landscape) long to short else short to long
    }

    /** 按捕获方向排列宽高（横/竖/自动）。 */
    private fun orient(width: Int, height: Int, orientation: CaptureOrientation): Pair<Int, Int> =
        when (orientation) {
            CaptureOrientation.LANDSCAPE -> if (width < height) height to width else width to height
            CaptureOrientation.PORTRAIT -> if (height < width) height to width else width to height
            CaptureOrientation.AUTO -> width to height
        }

    /**
     * 把边长向下对齐到 [alignment] 的整数倍（供编码器能力钳制复用）。
     *
     * [alignment] 非法（0/负）时原样返回，由调用方以 `coerceAtLeast(1)` 兜底，避免除零。
     */
    internal fun alignDown(value: Int, alignment: Int): Int =
        if (alignment <= 0) value else value / alignment * alignment
}
