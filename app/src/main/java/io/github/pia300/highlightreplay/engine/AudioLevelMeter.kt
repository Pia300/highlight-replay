package io.github.pia300.highlightreplay.engine

import kotlin.math.sqrt

/**
 * 由 PCM 块计算平滑音量（0..1）。
 *
 * 非静音时按平方根曲线提升低音量感知并保持快升，静音时快速衰减；
 * 低于 [noiseFloor] 的块视为静音，衰减到 0.001 以下归零。
 *
 * @param noiseFloor 视为静音的 RMS 门限。
 * @param releaseFactor 非静音块的回落系数（每块乘以此值）。
 */
internal class AudioLevelMeter(
    private val noiseFloor: Float = 0.002f,
    private val releaseFactor: Float = 0.96f
) {

    /** 未截断的平滑电平，参与下一块的衰减计算。 */
    private var smoothLevel = 0f

    /** 当前电平，已截断到 0..1。 */
    var level: Float = 0f
        private set

    /** 用一块 16 位有符号小端 PCM 更新电平并返回当前值。 */
    fun onSamples(buffer: ByteArray, read: Int): Float {
        var sumSquares = 0L
        var sampleCount = 0
        for (i in 0 until read - 1 step 2) {
            val sample =
                ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF))
            sumSquares += sample.toLong() * sample
            sampleCount++
        }
        // RMS 归一化到 0..1（16 位满幅 32768）。
        val rms = if (sampleCount > 0) {
            (sqrt(sumSquares.toDouble() / sampleCount) / 32768.0).toFloat()
        } else 0f

        // 静音快速衰减，否则以平方根曲线提升低音量感知。
        if (rms < noiseFloor) {
            smoothLevel *= 0.5f
            if (smoothLevel < 0.001f) smoothLevel = 0f
        } else {
            val boosted = sqrt(rms)
            smoothLevel = maxOf(boosted, smoothLevel * releaseFactor)
        }
        level = smoothLevel.coerceIn(0f, 1f)
        return level
    }
}
