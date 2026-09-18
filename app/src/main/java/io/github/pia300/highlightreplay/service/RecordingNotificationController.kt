package io.github.pia300.highlightreplay.service

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Handler
import io.github.pia300.highlightreplay.R
import io.github.pia300.highlightreplay.data.LanguagePrefs

/** 录制前台通知：构建、重建发布、标题覆盖与流状态采样刷新。 */
internal class RecordingNotificationController(
    private val service: Context,
    private val mainHandler: Handler,
    private val audioEnabled: () -> Boolean,
    private val isSessionStopping: () -> Boolean,
    private val videoActiveProvider: () -> Boolean,
    private val audioActiveProvider: () -> Boolean,
    private val onTileUpdate: () -> Unit
) {

    /** 通知标题覆盖（保存中/已保存…）。 */
    @Volatile
    private var titleOverride: String? = null

    private var titleResetRunnable: Runnable? = null

    private var currentRecordingNotification: NotificationFactory.RecordingNotification? = null

    private val streamMonitor = RecordingStreamMonitor(
        mainHandler = mainHandler,
        videoActiveProvider = videoActiveProvider,
        audioActiveProvider = audioActiveProvider,
        onStateChanged = { videoActive, audioActive ->
            val holder = currentRecordingNotification ?: return@RecordingStreamMonitor
            NotificationFactory.applyStreamState(
                service,
                holder.views,
                videoActive,
                audioEnabled(),
                audioActive
            )
            notifyRecording(holder)
        }
    )

    fun createNotification(): Notification {
        return NotificationFactory.createRecordingNotification(
            service,
            FloatingControlService.isRunning,
            audioEnabled(),
            titleOverride
        ).notification
    }

    /** 重建并发布前台通知；可选附带磁贴刷新。 */
    fun refresh(notifyTile: Boolean = false) {
        if (!RecorderRuntimeState.currentState().isRunning || isSessionStopping()) return
        val holder = NotificationFactory.createRecordingNotification(
            service,
            FloatingControlService.isRunning,
            audioEnabled(),
            titleOverride
        )
        currentRecordingNotification = holder
        if (streamMonitor.hasSampled()) {
            NotificationFactory.applyStreamState(
                service,
                holder.views,
                streamMonitor.videoActive(),
                audioEnabled(),
                streamMonitor.audioActive()
            )
        }
        notifyRecording(holder)
        if (notifyTile) onTileUpdate()
    }

    /** 置“保存中”标题并刷新通知。 */
    fun showSavingTitle() {
        titleOverride = str(R.string.notification_saving_title)
        refresh()
    }

    /** 置“已保存”标题并刷新通知，随后延迟恢复默认标题。 */
    fun showSavedTitleAndScheduleReset() {
        titleOverride = str(R.string.notification_saved_title)
        refresh()
        val reset = Runnable {
            titleResetRunnable = null
            titleOverride = null
            refresh()
        }
        titleResetRunnable?.let { mainHandler.removeCallbacks(it) }
        titleResetRunnable = reset
        mainHandler.postDelayed(reset, RecorderService.TITLE_RESET_DELAY_MS)
    }

    /** 取消延迟复位任务，清除标题覆盖并刷新通知。 */
    fun clearTitle() {
        titleResetRunnable?.let { mainHandler.removeCallbacks(it) }
        titleResetRunnable = null
        titleOverride = null
        refresh()
    }

    /** 停止会话时丢弃标题覆盖、延迟复位任务与当前通知持有。 */
    fun discardForStop() {
        titleResetRunnable?.let { mainHandler.removeCallbacks(it) }
        titleResetRunnable = null
        titleOverride = null
        currentRecordingNotification = null
    }

    /** 开始流状态采样（幂等）。 */
    fun startStreamMonitor() = streamMonitor.start()

    /** 停止流状态采样（幂等）。 */
    fun stopStreamMonitor() = streamMonitor.stop()

    private fun notifyRecording(holder: NotificationFactory.RecordingNotification) {
        service.getSystemService(NotificationManager::class.java)
            .notify(NotificationFactory.NOTIFICATION_ID, holder.notification)
    }

    private fun str(resId: Int): String = LanguagePrefs.string(service, resId)
}
