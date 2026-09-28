package io.github.pia300.highlightreplay.service

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import androidx.annotation.MainThread
import io.github.pia300.highlightreplay.engine.LinearityFilter
import io.github.pia300.highlightreplay.engine.ShakeDetector
import io.github.pia300.highlightreplay.engine.ShakeTuning

/**
 * 摇一摇监听：把传感器采样喂给 [ShakeDetector]，判定成立时回调 [onShake]。
 *
 * 开关与力度改动**即时生效**（设置页经 ACTION_UPDATE_SHAKE 下发），与会话设置快照
 * "录制参数下次生效"的语义不同：摇一摇只是触发通道，改了就应立即按新力度判定。
 *
 * 线程约定：注册/注销必须在主线程调用（[SensorManager.registerListener] 把回调投递到调用线程的
 * Looper）；[onShake] 统一投递到 [mainHandler]，使消费方无条件拿到主线程语义。
 */
internal class ShakeController(
    private val context: Context,
    private val mainHandler: Handler,
    private val onShake: () -> Unit
) {

    private val linearityFilter = LinearityFilter()

    // 复用数组承接去重力结果：传感器采样在热路径上，避免每帧分配。
    private val linear = FloatArray(3)

    private var detector: ShakeDetector? = null
    private var listener: SensorEventListener? = null

    // 传感器一律**延迟到首次使用**才查找（见 resolveSensors）：本类在 Service 的字段初始化期创建，
    // 那时 Service 尚未 attach 到 Context，任何 getSystemService 都会抛异常。
    private var sensorManager: SensorManager? = null
    private var linearSensor: Sensor? = null
    private var targetSensor: Sensor? = null
    private var resolved = false

    /** 已生效的配置：滑杆每次松手都会下发一次，相同配置直接跳过，避免反复注销/注册。 */
    private var applied: Pair<Boolean, Int>? = null

    /** 应用开关与力度；关闭或设备无传感器时注销监听。 */
    @MainThread
    fun update(enabled: Boolean, strength: Int) {
        val desired = enabled to strength
        if (applied == desired) return
        applied = desired
        unregister()
        if (!enabled) return
        resolveSensors()
        val sensor = targetSensor ?: return

        detector = ShakeDetector(amplitudeThreshold = ShakeTuning.amplitudeThreshold(strength))
        linearityFilter.reset()
        val callbacks = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) = handleSample(event)

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        listener = callbacks
        // SENSOR_DELAY_GAME（约 50Hz）：判定只需要波形，无需 FASTEST，省电且足够。
        sensorManager?.registerListener(callbacks, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    /** 注销监听并丢弃判定状态；下次 [update] 会重新注册。 */
    @MainThread
    fun stop() {
        applied = null
        unregister()
    }

    private fun resolveSensors() {
        if (resolved) return
        resolved = true
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        sensorManager = manager
        // 优先线性加速度（系统已完成去重力融合）；缺失时退回原始加速度计并自行扣除重力。
        linearSensor = manager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        targetSensor = linearSensor ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    private fun unregister() {
        listener?.let { sensorManager?.unregisterListener(it) }
        listener = null
        detector = null
    }

    private fun handleSample(event: SensorEvent) {
        val active = detector ?: return
        val values = event.values
        // event.timestamp 与 SystemClock.elapsedRealtime 同源（纳秒）；用它比每帧另取系统时钟更贴近采样时刻。
        val timestampMs = event.timestamp / NANOS_PER_MILLI
        val triggered = if (linearSensor != null) {
            active.onSample(values[0], values[1], values[2], timestampMs)
        } else {
            linearityFilter.toLinear(values[0], values[1], values[2], linear)
            active.onSample(linear[0], linear[1], linear[2], timestampMs)
        }
        if (triggered) mainHandler.post(onShake)
    }

    companion object {

        /**
         * 设备是否具备摇一摇检测所需的传感器。
         *
         * 设置页据此隐藏整个区块（与 H.265 不可用时不展示该选项同理，见 settingsOptionLists）：
         * 展示一个永远不生效的开关比不展示更容易让人以为功能坏了。
         */
        fun isSupported(context: Context): Boolean {
            val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
                ?: return false
            return manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
        }

        private const val NANOS_PER_MILLI = 1_000_000L
    }
}
