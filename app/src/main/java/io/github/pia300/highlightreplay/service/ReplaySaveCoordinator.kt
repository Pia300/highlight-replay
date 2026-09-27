package io.github.pia300.highlightreplay.service

import android.os.Handler
import android.widget.Toast
import io.github.pia300.highlightreplay.R
import io.github.pia300.highlightreplay.data.LanguagePrefs
import io.github.pia300.highlightreplay.data.RecorderSettings
import io.github.pia300.highlightreplay.engine.ReplaySaver
import io.github.pia300.highlightreplay.engine.ScreenRecorder
import io.github.pia300.highlightreplay.service.session.SessionEvent
import io.github.pia300.highlightreplay.service.session.SessionStateStore
import io.github.pia300.highlightreplay.ui.ToastCenter

/** 回放保存：触发保存、按序号忽略过期回调，并在完成/失败时复位进程级 isSaving。 */
internal class ReplaySaveCoordinator(
    private val service: RecorderService,
    private val mainHandler: Handler,
    private val notificationController: RecordingNotificationController,
    private val replaySaverProvider: () -> ReplaySaver?,
    private val screenRecorderProvider: () -> ScreenRecorder?,
    private val settingsProvider: () -> RecorderSettings?,
    private val notifyError: (String) -> Unit,
    private val finishPendingForegroundTeardown: () -> Unit
) {

    /** 保存请求序号：回调据此忽略被更新保存取代的过期结果。 */
    @Volatile
    private var saveGeneration = 0L

    fun triggerReplay() {
        val s = SessionStateStore.snapshot
        if (!s.isRunning) return
        if (!s.isCaptureReady) return // 管线尚未就绪（会话启动窗口内），忽略启动瞬间的保存点击
        if (s.isSaving) {
            notifyError(str(R.string.replay_saving_in_progress))
            return
        }
        val saver = replaySaverProvider() ?: run {
            notifyError(str(R.string.error_recorder_not_initialized))
            return
        }
        val sr = screenRecorderProvider()
        if (sr != null && !sr.isCaptureAlive()) {
            notifyError(str(R.string.error_projection_terminated))
            return
        }
        // 同步置位进程级 isSaving：本函数在主线程执行，先置位可封堵连点两次进入的窗口。
        SessionStateStore.reduce(SessionEvent.SaveStarted)
        // 触发即自增：回调据此忽略被更新保存取代的过期结果。
        val generation = ++saveGeneration
        saver.triggerSave(object : ReplaySaver.SaveCallback {
            override fun onSaveStarted() {
                mainHandler.post {
                    if (generation != saveGeneration) return@post
                    if (service.isLiveInstance()) {
                        notificationController.showSavingTitle()
                    }
                }
            }

            override fun onSaveCompleted() {
                mainHandler.post {
                    if (generation != saveGeneration) return@post
                    // 进程级 isSaving 无条件复位，不依赖实例存活。
                    SessionStateStore.reduce(SessionEvent.SaveFinished)
                    finishPendingForegroundTeardown()
                    if (!service.isLiveInstance()) return@post
                    notificationController.showSavedTitleAndScheduleReset()
                    ToastCenter.show(service, str(R.string.toast_replay_saved))
                }

            }

            override fun onSaveFailed(error: String) {
                mainHandler.post {
                    if (generation != saveGeneration) return@post
                    // 同 onSaveCompleted：isSaving 复位不依赖实例存活；UI 收尾仅在当前实例执行。
                    SessionStateStore.reduce(SessionEvent.SaveFinished)
                    finishPendingForegroundTeardown()
                    if (!service.isLiveInstance()) return@post
                    notificationController.clearTitle()
                    ToastCenter.show(service, error, Toast.LENGTH_LONG)
                }
            }
        })
    }

    fun createReplaySaver(): ReplaySaver {
        val sr = checkNotNull(screenRecorderProvider()) { "screenRecorder not initialized" }
        return ReplaySaver(
            service, checkNotNull(settingsProvider()) { "settings not initialized" },
            sr.getRingBuffer(), sr::getVideoFormat,
            sr.getAudioBuffer(), sr::getAudioFormat
        )
    }

    private fun str(resId: Int): String = LanguagePrefs.string(service, resId)
}
