package io.github.pia300.highlightreplay.engine

import org.junit.Assert.assertEquals
import org.junit.Test

/** 音量电平计算的单元测试。 */
class AudioLevelMeterTest {

    /** 浮点比较允许误差。 */
    private val delta = 1e-5f

    /** 构造由指定 16 位有符号样本组成的 PCM 块。 */
    private fun blockOf(vararg samples: Int): ByteArray {
        val out = ByteArray(samples.size * 2)
        samples.forEachIndexed { i, s ->
            out[i * 2] = (s and 0xFF).toByte()
            out[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
        }
        return out
    }

    /** 构造由同一数值重复组成的 PCM 块。 */
    private fun constantBlock(sample: Int, count: Int): ByteArray {
        val out = ByteArray(count * 2)
        for (i in 0 until count) {
            out[i * 2] = (sample and 0xFF).toByte()
            out[i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
        }
        return out
    }

    /** 静音块输出 0。 */
    @Test
    fun silenceStaysAtZero() {
        val meter = AudioLevelMeter()
        val block = constantBlock(0, 64)
        assertEquals(0f, meter.onSamples(block, block.size), delta)
    }

    /** 满幅块按平方根曲线归一化到 1。 */
    @Test
    fun fullScaleReachesOne() {
        val meter = AudioLevelMeter()
        val block = constantBlock(32767, 64)
        assertEquals(1f, meter.onSamples(block, block.size), 1e-4f)
    }

    /** 低音量按平方根曲线提升。 */
    @Test
    fun lowAmplitudeIsBoostedBySquareRoot() {
        val meter = AudioLevelMeter()
        val block = constantBlock(3277, 64)
        val rms = 3277.0 / 32768.0
        assertEquals(kotlin.math.sqrt(rms).toFloat(), meter.onSamples(block, block.size), 1e-4f)
    }

    /** 非静音块的回落不低于 releaseFactor 乘以当前电平。 */
    @Test
    fun releaseFactorLimitsDecay() {
        val meter = AudioLevelMeter()
        val loud = constantBlock(32767, 64)
        meter.onSamples(loud, loud.size)

        val quiet = constantBlock(328, 64)
        assertEquals(0.96f, meter.onSamples(quiet, quiet.size), 1e-4f)
    }

    /** 静音块使电平逐块减半。 */
    @Test
    fun silenceHalvesLevel() {
        val meter = AudioLevelMeter()
        val loud = constantBlock(32767, 64)
        meter.onSamples(loud, loud.size)

        val silent = constantBlock(0, 64)
        assertEquals(0.5f, meter.onSamples(silent, silent.size), 1e-4f)
        assertEquals(0.25f, meter.onSamples(silent, silent.size), 1e-4f)
    }

    /** 连续静音衰减到 0.001 以下后归零。 */
    @Test
    fun silenceEventuallyReachesZero() {
        val meter = AudioLevelMeter()
        val loud = constantBlock(32767, 64)
        meter.onSamples(loud, loud.size)

        val silent = constantBlock(0, 64)
        repeat(10) { meter.onSamples(silent, silent.size) }
        assertEquals(0f, meter.level, delta)
    }

    /** 不足一个完整样本的字节数只消费完整样本，不抛异常。 */
    @Test
    fun oddByteCountConsumesCompleteSamplesOnly() {
        val meter = AudioLevelMeter()
        val block = blockOf(32767, 32767, 32767)
        assertEquals(1f, meter.onSamples(block, 3), 1e-4f)
    }

    /** 读取长度为 0 时按静音处理。 */
    @Test
    fun zeroLengthIsTreatedAsSilence() {
        val meter = AudioLevelMeter()
        val block = constantBlock(32767, 64)
        meter.onSamples(block, block.size)
        assertEquals(0.5f, meter.onSamples(block, 0), 1e-4f)
    }

    /** 负样本按有符号 16 位解释，绝对值参与平方和。 */
    @Test
    fun negativeSamplesUseSignedMagnitude() {
        val meter = AudioLevelMeter()
        val block = constantBlock(-32768, 64)
        assertEquals(1f, meter.onSamples(block, block.size), 1e-4f)
    }
}
