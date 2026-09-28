package io.github.pia300.highlightreplay.engine

import io.github.pia300.highlightreplay.data.RecorderSettings
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ShakeDetector] 的判定行为：既要认出真正的摇动，也要放过走路与单次冲击。
 *
 * 采样一律按 50Hz（20ms 间隔）喂入，与生产环境的 SENSOR_DELAY_GAME 一致。
 */
class ShakeDetectorTest {

    private val stepMs = 20L
    private val threshold = 12f

    private fun detector() = ShakeDetector(amplitudeThreshold = threshold)

    /** 沿某个轴的 5Hz 正弦采样，峰值 [amplitude]。 */
    private fun shakeSample(axis: Int, amplitude: Float, tMs: Long): Triple<Float, Float, Float> {
        val value = amplitude * sin(2.0 * PI * 5.0 * tMs / 1000.0).toFloat()
        return when (axis) {
            0 -> Triple(value, 0f, 0f)
            1 -> Triple(0f, value, 0f)
            else -> Triple(0f, 0f, value)
        }
    }

    /** 持续摇动（峰值 20 > 阈值 12，500ms 窗口内约 5 次换向）必须触发。 */
    @Test
    fun sustainedShakeTriggers() {
        val detector = detector()
        var triggeredAt = -1L
        for (t in 0L..2000L step stepMs) {
            val (x, y, z) = shakeSample(0, 20f, t)
            if (detector.onSample(x, y, z, t)) {
                triggeredAt = t
                break
            }
        }
        assertTrue("持续摇动应在 2 秒内触发", triggeredAt in 1..2000)
    }

    /** 摇动方向任意：主轴换到 Y 轴同样要认出来。 */
    @Test
    fun shakeOnSecondaryAxisTriggers() {
        val detector = detector()
        var triggered = false
        for (t in 0L..2000L step stepMs) {
            val (x, y, z) = shakeSample(1, 20f, t)
            if (detector.onSample(x, y, z, t)) triggered = true
        }
        assertTrue("Y 轴摇动应触发", triggered)
    }

    /** 走路级别的小幅低频晃动（2Hz、峰值 4 < 阈值）不得触发。 */
    @Test
    fun walkingLevelMotionDoesNotTrigger() {
        val detector = detector()
        for (t in 0L..6000L step stepMs) {
            val value = 4f * sin(2.0 * PI * 2.0 * t / 1000.0).toFloat()
            assertFalse("走路幅度不应触发（t=${t}ms）", detector.onSample(value, 0f, 0f, t))
        }
    }

    /** 单次冲击（磕碰/放桌上）峰值可以很高，但只有一次换向，不得触发。 */
    @Test
    fun singleJoltDoesNotTrigger() {
        val detector = detector()
        var triggered = false
        for (t in 0L..3000L step stepMs) {
            // 300~500ms 之间一个三角脉冲（峰值 30），其余时间静止。
            val pulse = when {
                t in 300L..400L -> (t - 300L) / 100f * 30f
                t in 400L..500L -> (500L - t) / 100f * 30f
                else -> 0f
            }
            if (detector.onSample(pulse, 0f, 0f, t)) triggered = true
        }
        assertFalse("单次冲击不应触发", triggered)
    }

    /** 触发后冷却期内持续摇动不得重复触发；冷却结束后应能再次触发。 */
    @Test
    fun cooldownSuppressesRepeatedTriggers() {
        val detector = detector()
        var firstTrigger = -1L
        for (t in 0L..2000L step stepMs) {
            val (x, y, z) = shakeSample(0, 20f, t)
            if (detector.onSample(x, y, z, t)) {
                firstTrigger = t
                break
            }
        }
        assertTrue("先要有一次触发", firstTrigger >= 0)

        var secondTrigger = -1L
        var t = firstTrigger + stepMs
        while (t <= firstTrigger + ShakeDetector.COOLDOWN_MS + 2000L) {
            val (x, y, z) = shakeSample(0, 20f, t)
            if (detector.onSample(x, y, z, t)) {
                secondTrigger = t
                break
            }
            t += stepMs
        }
        assertTrue("冷却结束后应能再次触发", secondTrigger >= 0)
        assertTrue(
            "重复触发至少间隔冷却时长：first=$firstTrigger, second=$secondTrigger",
            secondTrigger - firstTrigger >= ShakeDetector.COOLDOWN_MS
        )
    }

    /** 力度换算：两端取到最低/最高阈值，越界钳制，且随力度单调递增。 */
    @Test
    fun amplitudeThresholdGrowsWithStrength() {
        val floor = RecorderSettings.SHAKE_STRENGTH_RANGE.first
        val ceiling = RecorderSettings.SHAKE_STRENGTH_RANGE.last

        // 力度下限 10 对应约 9.6 m/s²，仍在走路峰值之上。
        assertEquals(9.6f, ShakeTuning.amplitudeThreshold(floor), 0.001f)
        assertEquals(24f, ShakeTuning.amplitudeThreshold(ceiling), 0.001f)
        assertEquals(9.6f, ShakeTuning.amplitudeThreshold(floor - 1), 0.001f)
        assertEquals(24f, ShakeTuning.amplitudeThreshold(999), 0.001f)

        var previous = Float.NEGATIVE_INFINITY
        for (strength in floor..ceiling) {
            val current = ShakeTuning.amplitudeThreshold(strength)
            assertTrue("力度 $strength 的阈值应严格递增", current > previous)
            previous = current
        }
    }

    /** 力度越大越难触发：同样幅度的摇动在低力度下触发、高力度下不触发。 */
    @Test
    fun higherStrengthRequiresLargerAmplitude() {
        val permissive = ShakeDetector(
            amplitudeThreshold = ShakeTuning.amplitudeThreshold(RecorderSettings.SHAKE_STRENGTH_RANGE.first)
        )
        val strict = ShakeDetector(
            amplitudeThreshold = ShakeTuning.amplitudeThreshold(RecorderSettings.SHAKE_STRENGTH_RANGE.last)
        )
        var permissiveTriggered = false
        var strictTriggered = false
        for (t in 0L..2000L step stepMs) {
            val (x, y, z) = shakeSample(0, 14f, t)
            if (permissive.onSample(x, y, z, t)) permissiveTriggered = true
            if (strict.onSample(x, y, z, t)) strictTriggered = true
        }
        assertTrue("最低力度下 14m/s² 的摇动应触发", permissiveTriggered)
        assertFalse("最高力度下同样的摇动不应触发", strictTriggered)
    }
}
