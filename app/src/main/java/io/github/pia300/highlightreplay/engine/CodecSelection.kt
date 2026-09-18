package io.github.pia300.highlightreplay.engine

/**
 * 编码器选择与输出尺寸钳制的**唯一决策点**。
 *
 * 把「选哪个 codec」与「用多大尺寸」放在同一次决策里，避免出现
 * 「按 codec A 的能力缩小尺寸、却用 codec B 编码」的不一致——那样钳制等于没做。
 *
 * @param codecName 选中的编码器名；null 表示交给 `MediaCodec.createEncoderByType` 由系统决定。
 * @param width 实际使用的输出宽
 * @param height 实际使用的输出高
 * @param hardware 选中项是否为硬件编码器；系统代选时为 null（未知）
 * @param clamped 尺寸是否被能力钳制过（用于日志与诊断）
 */
internal data class EncoderPlan(
    val codecName: String?,
    val width: Int,
    val height: Int,
    val hardware: Boolean?,
    val clamped: Boolean
)

/** 候选编码器的纯数据视图：使选型逻辑可脱离 MediaCodecList 单测。 */
internal data class CodecCandidate(
    val name: String,
    val hardware: Boolean,
    /** 支持的最小/最大宽；null 表示能力未知。 */
    val minWidth: Int?,
    val maxWidth: Int?,
    val minHeight: Int?,
    val maxHeight: Int?,
    /** 宽高对齐步长；null 表示能力未知。 */
    val widthAlignment: Int?,
    val heightAlignment: Int?
)

/** 等比缩小的最大尝试次数与最小边长，防止能力未知时死循环。 */
private const val MAX_SHRINK_STEPS = 40
private const val MIN_SHRINK_EDGE = 320

/** 候选是否支持该尺寸；能力未知时返回 null（无法判断）。 */
private fun CodecCandidate.supports(w: Int, h: Int): Boolean? {
    val minW = minWidth ?: return null
    val maxW = maxWidth ?: return null
    val minH = minHeight ?: return null
    val maxH = maxHeight ?: return null
    return w in minW..maxW && h in minH..maxH
}

/**
 * 把 [w]×[h] 收缩到候选支持范围内：
 * 先按宽高对齐裁剪，再等比缩小直到受支持；仍不支持时返回 null（由调用方换下一个候选）。
 * 能力未知（范围字段缺失）时返回原尺寸——无法判断是否受支持，交由 configure 决定。
 */
internal fun clampToCandidate(candidate: CodecCandidate, w: Int, h: Int): Pair<Int, Int>? {
    // 能力未知：不缩尺寸。
    if (candidate.supports(w, h) == null) return w to h

    val widthAlignment = (candidate.widthAlignment ?: 1).coerceAtLeast(1)
    val heightAlignment = (candidate.heightAlignment ?: 1).coerceAtLeast(1)
    var clampedW = VideoSizeCalculator.alignDown(w, widthAlignment)
    var clampedH = VideoSizeCalculator.alignDown(h, heightAlignment)
    var supported = candidate.supports(clampedW, clampedH) == true

    var guard = 0
    while (!supported && guard++ < MAX_SHRINK_STEPS &&
        clampedW > MIN_SHRINK_EDGE && clampedH > MIN_SHRINK_EDGE
    ) {
        clampedW = VideoSizeCalculator.alignDown(clampedW * 9 / 10, widthAlignment)
        clampedH = VideoSizeCalculator.alignDown(clampedH * 9 / 10, heightAlignment)
        supported = candidate.supports(clampedW, clampedH) == true
    }
    return if (supported) clampedW to clampedH else null
}

/**
 * 纯决策核心：在 [candidates] 中按 [wantHardware] 选型并钳制尺寸。
 *
 * - 优先返回第一个「尺寸可满足」的候选；
 * - 能力未知的候选按原尺寸接受（无法判断，交由 configure 决定）；
 * - 全部不满足时返回 null，由调用方回退系统代选。
 *
 * @param candidates 候选编码器（已按 MIME 过滤）。
 * @param wantHardware 是否只要硬件编码器。
 */
internal fun selectEncoder(
    candidates: List<CodecCandidate>,
    wantWidth: Int,
    wantHeight: Int,
    wantHardware: Boolean
): EncoderPlan? {
    for (candidate in candidates) {
        if (candidate.hardware != wantHardware) continue
        val clamped = clampToCandidate(candidate, wantWidth, wantHeight) ?: continue
        val changed = clamped.first != wantWidth || clamped.second != wantHeight
        return EncoderPlan(candidate.name, clamped.first, clamped.second, wantHardware, clamped = changed)
    }
    return null
}
