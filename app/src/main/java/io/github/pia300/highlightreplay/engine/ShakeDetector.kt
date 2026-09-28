package io.github.pia300.highlightreplay.engine

import io.github.pia300.highlightreplay.data.RecorderSettings
import kotlin.math.sqrt

/**
 * 「触发力度」档位到判定阈值的换算。
 *
 * 阈值是**线性加速度峰值**（m/s²，重力已去除），可直接与常见动作对照：
 * 手持走动约 2~5、抬手放下约 5~8、刻意摇动约 15~25。故下限取 8——再低会把走路误判为摇动；
 * 上限取 24——再高则正常用力也摇不出来。中间线性插值，使滑杆全程都有可见效果。
 */
internal object ShakeTuning {

    private const val MIN_AMPLITUDE = 8f
    private const val MAX_AMPLITUDE = 24f

    /** 触发力度（[RecorderSettings.SHAKE_STRENGTH_RANGE]，越大约难触发）对应的峰值阈值。 */
    fun amplitudeThreshold(strength: Int): Float {
        val clamped = strength.coerceIn(RecorderSettings.SHAKE_STRENGTH_RANGE)
        val ratio = clamped.toFloat() / RecorderSettings.SHAKE_STRENGTH_RANGE.endInclusive
        return MIN_AMPLITUDE + ratio * (MAX_AMPLITUDE - MIN_AMPLITUDE)
    }
}

/**
 * 摇一摇判定器（纯逻辑，不含 Android 依赖，可在 JVM 上单测）。
 *
 * 输入是**已去重力**的线性加速度（m/s²）与单调时钟毫秒戳。判定只看最近 [windowMs] 的波形，
 * 两个条件同时成立才算一次摇动：
 *
 *  1. 峰值条件：窗口内线性加速度峰值 ≥ [amplitudeThreshold]；
 *  2. 换向条件：窗口内主轴信号的方向反转次数 ≥ [minReversals]。
 *
 * 换向条件是必需的：单次冲击（磕到桌沿、放桌上、手里一抖）的峰值同样可以很高，但只有一次
 * 方向变化；摇动则会在数百毫秒内反复换向。只看峰值必然把单次冲击误判成摇动。
 *
 * 触发后进入 [cooldownMs] 冷却并清空窗口，使一次摇动只保存一次回放（保存自身需 1~3 秒）。
 * 本类非线程安全：由传感器回调所在线程独占使用。
 */
internal class ShakeDetector(
    private val amplitudeThreshold: Float,
    private val windowMs: Long = WINDOW_MS,
    private val cooldownMs: Long = COOLDOWN_MS,
    private val minReversals: Int = MIN_REVERSALS,
    bufferCapacity: Int = DEFAULT_CAPACITY
) {

    // 独立于构造参数命名：容量钳制后是采样窗口的硬上限，两者不是同一个概念。
    private val capacity = bufferCapacity.coerceAtLeast(MIN_CAPACITY)

    // 环形缓冲：三轴 + 时间戳并行数组，按需覆盖最旧样本，避免每帧分配。
    private val xs = FloatArray(capacity)
    private val ys = FloatArray(capacity)
    private val zs = FloatArray(capacity)
    private val timestamps = LongArray(capacity)
    private var head = 0
    private var size = 0
    private var lastTriggerMs = NO_TRIGGER

    /** 追加一个采样；判定成立时返回 true，并进入冷却。 */
    fun onSample(x: Float, y: Float, z: Float, timestampMs: Long): Boolean {
        push(x, y, z, timestampMs)
        if (lastTriggerMs != NO_TRIGGER && timestampMs - lastTriggerMs < cooldownMs) return false

        var count = 0
        var peak = 0f
        var sumX = 0f
        var sumY = 0f
        var sumZ = 0f
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var minZ = Float.MAX_VALUE
        var maxZ = -Float.MAX_VALUE
        forEachSampleInWindow(timestampMs) { index ->
            val vx = xs[index]
            val vy = ys[index]
            val vz = zs[index]
            count++
            val magnitude = sqrt(vx * vx + vy * vy + vz * vz)
            if (magnitude > peak) peak = magnitude
            sumX += vx
            sumY += vy
            sumZ += vz
            if (vx < minX) minX = vx
            if (vx > maxX) maxX = vx
            if (vy < minY) minY = vy
            if (vy > maxY) maxY = vy
            if (vz < minZ) minZ = vz
            if (vz > maxZ) maxZ = vz
        }
        if (count < MIN_SAMPLES || peak < amplitudeThreshold) return false

        // 主轴取峰峰值最大的轴：摇动方向任意，只有主能量轴的过零次数才代表换向。
        val axis = dominantAxis(maxX - minX, maxY - minY, maxZ - minZ)
        val mean = when (axis) {
            AXIS_X -> sumX / count
            AXIS_Y -> sumY / count
            else -> sumZ / count
        }

        var reversals = 0
        var previousSign = 0
        forEachSampleInWindow(timestampMs) { index ->
            val deviation = axisValue(index, axis) - mean
            val sign = when {
                deviation > REVERSAL_DEADBAND -> 1
                deviation < -REVERSAL_DEADBAND -> -1
                else -> 0
            }
            if (sign != 0) {
                if (previousSign != 0 && sign != previousSign) reversals++
                previousSign = sign
            }
        }
        if (reversals < minReversals) return false

        lastTriggerMs = timestampMs
        clearWindow()
        return true
    }

    /** 复位：清空窗口并取消冷却（会话开始或设置变更后调用）。 */
    fun reset() {
        clearWindow()
        lastTriggerMs = NO_TRIGGER
    }

    private fun push(x: Float, y: Float, z: Float, timestampMs: Long) {
        xs[head] = x
        ys[head] = y
        zs[head] = z
        timestamps[head] = timestampMs
        head = (head + 1) % capacity
        if (size < capacity) size++
    }

    private fun clearWindow() {
        head = 0
        size = 0
    }

    private fun dominantAxis(rangeX: Float, rangeY: Float, rangeZ: Float): Int = when {
        rangeX >= rangeY && rangeX >= rangeZ -> AXIS_X
        rangeY >= rangeZ -> AXIS_Y
        else -> AXIS_Z
    }

    private fun axisValue(index: Int, axis: Int): Float = when (axis) {
        AXIS_X -> xs[index]
        AXIS_Y -> ys[index]
        else -> zs[index]
    }

    /** 按时间先后遍历窗口内的样本下标。 */
    private inline fun forEachSampleInWindow(timestampMs: Long, action: (Int) -> Unit) {
        val cutoff = timestampMs - windowMs
        val start = if (size == capacity) head else 0
        for (offset in 0 until size) {
            val index = (start + offset) % capacity
            if (timestamps[index] < cutoff) continue
            action(index)
        }
    }

    companion object {
        /** 判定窗口（毫秒）：覆盖一次摇动的完整波形。 */
        const val WINDOW_MS = 500L

        /** 触发冷却（毫秒）：一次摇动只保存一次回放。 */
        const val COOLDOWN_MS = 2000L

        /** 换向次数下限：低于此值视为单次冲击或低频摆动（走路约 2 次/秒）。 */
        const val MIN_REVERSALS = 3

        /** 换向死区（m/s²）：偏移小于该值不计方向，滤掉零附近噪声。 */
        private const val REVERSAL_DEADBAND = 0.5f

        /** 判定所需最少样本：窗口未填满也允许判定，但样本太少没有统计意义。 */
        private const val MIN_SAMPLES = 4

        /** 环形缓冲容量：500ms 窗口在 400Hz 采样下也不溢出。 */
        private const val DEFAULT_CAPACITY = 256
        private const val MIN_CAPACITY = 8

        private const val NO_TRIGGER = Long.MIN_VALUE
        private const val AXIS_X = 0
        private const val AXIS_Y = 1
        private const val AXIS_Z = 2
    }
}
