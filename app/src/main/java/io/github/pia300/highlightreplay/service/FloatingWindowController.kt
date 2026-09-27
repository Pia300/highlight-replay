package io.github.pia300.highlightreplay.service

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.content.edit
import io.github.pia300.highlightreplay.R
import io.github.pia300.highlightreplay.data.LanguagePrefs
import io.github.pia300.highlightreplay.data.RecorderPrefs
import io.github.pia300.highlightreplay.data.defaultPrefs
import io.github.pia300.highlightreplay.data.getBooleanSafe
import io.github.pia300.highlightreplay.service.session.SessionStateStore
import io.github.pia300.highlightreplay.ui.ToastCenter

/** 悬浮球联动：拉起/停止悬浮球服务与显隐开关。 */
internal class FloatingWindowController(
    private val service: Context,
    private val notificationController: RecordingNotificationController
) {

    private companion object {
        // 日志沿用录制服务标签。
        const val TAG = "RecorderService"
    }

    /**
     * 启动悬浮球服务；系统拒绝时仅记日志，不让异常冒泡。
     *
     * 本服务在前台时启动另一服务是允许的，但 Android 12+ 的后台启动限制、部分 ROM 的额外策略
     * 以及悬浮窗权限被撤销的竞态都可能抛异常；这里统一兜底，避免「点一下通知按钮就崩溃」。
     */
    private fun startFloatingServiceSafely() {
        try {
            service.startService(Intent(service, FloatingControlService::class.java))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start floating service: ${e.message}")
            ToastCenter.show(service, str(R.string.notif_float_open_failed), Toast.LENGTH_LONG)
        }
    }

    /** 悬浮球显隐（会话运行且未隐藏且已授权时显示）。 */
    fun ensureFloatingService(started: Boolean) {
        if (started) {
            val prefs = service.defaultPrefs()
            val hidden = prefs.getBooleanSafe(RecorderPrefs.KEY_FLOATING_HIDDEN, false)
            val autoShow = prefs.getBooleanSafe(
                RecorderPrefs.KEY_AUTO_SHOW_FLOATING,
                RecorderPrefs.AUTO_SHOW_FLOATING_DEFAULT
            )
            val granted = android.provider.Settings.canDrawOverlays(service)
            if (!hidden && autoShow && granted) {
                startFloatingServiceSafely()
            }
        } else {
            service.stopService(Intent(service, FloatingControlService::class.java))
        }
    }

    /** 通知/磁贴上的悬浮窗开关：按实际可见性反转（可见→隐藏，不可见→显示），单次点击即达预期状态。 */
    fun toggleFloatingVisibility() {
        if (!SessionStateStore.snapshot.isRunning) return
        val prefs = service.defaultPrefs()
        val show = !FloatingControlService.isRunning
        prefs.edit { putBoolean(RecorderPrefs.KEY_FLOATING_HIDDEN, !show) }
        if (show) {
            if (!android.provider.Settings.canDrawOverlays(service)) {
                ToastCenter.show(service, str(R.string.notif_float_open_failed), Toast.LENGTH_LONG)
            } else {
                startFloatingServiceSafely()
            }
        } else {
            service.stopService(Intent(service, FloatingControlService::class.java))
        }
        notificationController.refresh(notifyTile = true)
    }

    private fun str(resId: Int): String = LanguagePrefs.string(service, resId)
}
