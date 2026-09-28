package io.github.pia300.highlightreplay.engine

/**
 * 原始加速度计的去重力滤波（纯逻辑，可在 JVM 上单测）。
 *
 * 线性加速度虚拟传感器（TYPE_LINEAR_ACCELERATION）并非所有设备都提供，缺失时只能读原始
 * 加速度计。原始值里混着约 9.8 m/s² 的重力，必须扣除后才能按"峰值 + 换向"判定摇动——
 * 否则手机平放时就已经超过任何阈值。
 *
 * 用一阶低通估计重力（[alpha] 越大越平滑、跟随越慢）：重力方向在数秒内基本不变，
 * 而摇动在数百毫秒内变化，故低通能分离两者。
 *
 * 本类非线程安全：由传感器回调所在线程独占使用。
 */
internal class LinearityFilter(private val alpha: Float = DEFAULT_ALPHA) {

    private val gravity = FloatArray(3)
    private var primed = false

    /** 复位：下一次采样重新建立重力基准。 */
    fun reset() {
        gravity.fill(0f)
        primed = false
    }

    /**
     * 把去重力后的线性加速度写入 [out] 并返回 [out]（复用调用方数组，避免每个采样分配）。
     *
     * 首个采样直接以自身作为重力基准：否则第一帧会把整段重力当成一次巨大冲击。
     */
    fun toLinear(x: Float, y: Float, z: Float, out: FloatArray): FloatArray {
        if (!primed) {
            gravity[0] = x
            gravity[1] = y
            gravity[2] = z
            primed = true
        } else {
            gravity[0] = alpha * gravity[0] + (1f - alpha) * x
            gravity[1] = alpha * gravity[1] + (1f - alpha) * y
            gravity[2] = alpha * gravity[2] + (1f - alpha) * z
        }
        out[0] = x - gravity[0]
        out[1] = y - gravity[1]
        out[2] = z - gravity[2]
        return out
    }

    private companion object {
        /** 低通系数：50Hz 采样下时间常数约 0.1 秒，远慢于摇动。 */
        const val DEFAULT_ALPHA = 0.8f
    }
}
