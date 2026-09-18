package io.github.pia300.highlightreplay.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import io.github.pia300.highlightreplay.R
import io.github.pia300.highlightreplay.data.LanguagePrefs
import io.github.pia300.highlightreplay.data.defaultPrefs
import io.github.pia300.highlightreplay.ui.ToastCenter

/** 悬浮球服务（普通后台服务，随录制会话生灭）：单击保存回放、长按停止会话，显隐由 RecorderService 驱动。 */
class FloatingControlService : Service() {

    companion object {
        private const val TAG = "FloatingControlService"
        /** 设置已更新：刷新悬浮窗尺寸/透明度/位置。 */
        const val ACTION_UPDATE_SETTINGS = "action_update_settings"

        /** 随 [ACTION_UPDATE_SETTINGS] 携带：为 true 时把悬浮球移回默认位置。 */
        const val EXTRA_RESET_POSITION = "extra_reset_position"
        // 无专用显隐 action：startService（无 action）建窗、stopService 收窗。

        /** 单击保存回放的防抖间隔（毫秒）。 */
        private const val TAP_DEBOUNCE_MS = 800L

        // @Volatile 跨线程可见；RecorderService 读取以决定是否发帧广播。
        @Volatile
        var isRunning = false
            private set

        // 悬浮窗是否已挂载到 WindowManager；RecorderService 据此对“服务在跑但窗口丢失”做自愈。
        @Volatile
        var windowVisible = false
            private set
    }

    private lateinit var prefs: SharedPreferences

    /** 悬浮窗：视图、布局参数与手势都归它管理。 */
    private var overlayWindow: FloatingOverlayWindow? = null

    // 上次触发回放时间（elapsedRealtime 单调时钟），单击防抖用。
    private var lastReplayAt = 0L

    private val frameActivityReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != RecorderService.ACTION_FRAME_ACTIVITY) return
            val videoActive = intent.getBooleanExtra(RecorderService.EXTRA_VIDEO_ACTIVE, false)
            val amplitude = intent.getFloatExtra(RecorderService.EXTRA_AUDIO_AMPLITUDE, 0f)
            val window = overlayWindow ?: return
            window.updateIndicators(videoActive, amplitude)
            // 旋转兜底：系统未必把 onConfigurationChanged 派发给 Service。
            window.syncOrientationIfNeeded()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** 配置变化时重算悬浮窗尺寸并纠正越界位置。 */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)

        overlayWindow?.onConfigurationChanged()
    }

    /** 初始化偏好设置与悬浮窗对象，并注册帧活动广播接收器。 */
    override fun onCreate() {
        super.onCreate()
        prefs = defaultPrefs()
        isRunning = true
        overlayWindow = FloatingOverlayWindow(
            context = this,
            prefs = prefs,
            onTap = { onButtonClick() },
            onLongPress = { onLongPress() },
            onAddFailed = {
                windowVisible = false
                stopSelf()
            }
        )

        registerCompat(frameActivityReceiver, IntentFilter(RecorderService.ACTION_FRAME_ACTIVITY))
        notifyHostStateChanged()
    }

    /**
     * 请求宿主服务重建前台通知与磁贴：本服务的启停经 AMS 异步派发，
     * 宿主在 startService/stopService 之后立刻读取 isRunning 会拿到旧值（开关图标/磁贴文案滞后）。
     */
    private fun notifyHostStateChanged() {
        if (!RecorderService.isRunning) return
        try {
            startService(
                Intent(this, RecorderService::class.java).apply {
                    action = RecorderService.ACTION_REFRESH_NOTIFICATION
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to request host notification refresh: ${e.message}")
        }
    }

    /** 广播注册兼容：Android 13+ 须显式指定导出标志。 */
    private fun registerCompat(receiver: BroadcastReceiver, filter: IntentFilter) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // 仅接收本应用广播（非导出）。
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            // 旧版本无导出标志参数：以签名级权限约束发送方，防第三方伪造帧活动广播。
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(
                receiver,
                filter,
                RecorderService.INTERNAL_BROADCAST_PERMISSION,
                null
            )
        }
    }

    /** 命令处理：无 action 建窗（幂等）、UPDATE_SETTINGS 就地刷新；收窗由 stopService 完成。非粘性：服务随会话生灭。 */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val window = overlayWindow ?: return START_NOT_STICKY
        when (intent.action) {
            ACTION_UPDATE_SETTINGS -> {
                if (window.hasView()) {
                    window.refreshSettings(intent.getBooleanExtra(EXTRA_RESET_POSITION, false))
                } else {
                    // 窗口已丢失（如被系统回收）：重建；权限缺失时 addView 失败并自行停服。
                    window.show()
                }
            }
            else -> {
                // 直接启动：建窗（幂等）。
                if (!window.hasView()) window.show()
            }
        }
        windowVisible = window.isVisible
        return START_NOT_STICKY
    }

    /** 长按悬浮按钮：停止整个录制会话。 */
    private fun onLongPress() {
        if (!RecorderService.isRunning) {
            // 会话已结束（停止竞态窗口）：仅自行收尾。
            stopSelf()
            return
        }
        try {
            startService(Intent(this, RecorderService::class.java).apply {
                action = RecorderService.ACTION_STOP
            })
        } catch (e: Exception) {
            Log.w(TAG, "startService failed to stop recording: ${e.message}")
            return
        }
        ToastCenter.show(
            this,
            LanguagePrefs.string(this, R.string.toast_stopping_recording),
            Toast.LENGTH_SHORT
        )
    }

    /** 单击悬浮按钮：保存回放（保存中忽略，带 800ms 防抖）。 */
    private fun onButtonClick() {
        if (!RecorderService.isRunning) {
            stopSelf()
            return
        }
        // 保存中忽略单击，避免打断在途保存。
        if (RecorderService.isSaving) return
        // 采集管线尚未就绪（编码器输出格式未到）时保存会被宿主静默忽略，此处直接提示，避免「点了没反应」。
        if (!RecorderService.captureReady) {
            ToastCenter.show(
                this,
                LanguagePrefs.string(this, R.string.error_recorder_not_initialized),
                Toast.LENGTH_SHORT
            )
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastReplayAt < TAP_DEBOUNCE_MS) return
        try {
            startService(Intent(this, RecorderService::class.java).apply {
                action = RecorderService.ACTION_TRIGGER_REPLAY
            })
        } catch (e: Exception) {
            Log.w(TAG, "startService failed to trigger replay: ${e.message}")
            return
        }
        // 仅在命令确实发出后才计防抖，避免发送失败时把用户锁在防抖窗口内。
        lastReplayAt = now
        ToastCenter.show(
            this,
            LanguagePrefs.string(this, R.string.toast_replay_saving),
            Toast.LENGTH_SHORT
        )
    }

    /** 销毁：注销广播、移除悬浮窗并通知宿主刷新状态。 */
    override fun onDestroy() {
        super.onDestroy()
        windowVisible = false
        // 注销可能因从未注册而失败，静默忽略。
        try {
            unregisterReceiver(frameActivityReceiver)
        } catch (_: Exception) {
        }

        overlayWindow?.destroy()
        overlayWindow = null
        isRunning = false
        notifyHostStateChanged()
    }
}
