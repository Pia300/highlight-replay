package io.github.pia300.highlightreplay.service

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import io.github.pia300.highlightreplay.engine.ScreenRecorder
import io.github.pia300.highlightreplay.service.session.SessionEvent
import io.github.pia300.highlightreplay.service.session.SessionStateStore

/**
 * 采集管线是否已产出可封装的轨道格式。
 *
 * 保存必须能取到全部轨道的输出格式才能注册音轨，故启用音频时音频格式同为必要条件；
 * 仅视频格式就绪时保存会静默丢弃音轨。
 */
internal fun capturePipelineReady(
    videoFormatReady: Boolean,
    audioEnabled: Boolean,
    audioFormatReady: Boolean
): Boolean = videoFormatReady && (!audioEnabled || audioFormatReady)

/** 会话 tick 与帧活动广播：驱动已录时长刷新、采集就绪置位与悬浮球帧活动广播。 */
internal class FrameActivityBroadcaster(
    private val service: Context,
    private val mainHandler: Handler,
    private val sessionStartMsProvider: () -> Long,
    private val screenRecorderProvider: () -> ScreenRecorder?,
    private val captureReadyProvider: () -> Boolean,
    /**
     * 「画面是否活跃」的唯一判据提供方。
     *
     * 必须由 RecorderService 注入 engine/CaptureLiveness.videoStreamActive（渲染侧在出帧
     * 且编码器帧数仍在推进）；本类若自行用 hasRecentVideoOutput 拼判据，悬浮球指示灯就会在
     * 排空线程死亡时仍显示「活跃」，与通知给出的「中断」互相矛盾。
     */
    private val videoActiveProvider: () -> Boolean,
    private val onCaptureReady: () -> Unit
) {

    private companion object {
        // 日志沿用录制服务标签。
        const val TAG = "RecorderService"
    }

    /** 会话 tick 拍数：每会话复位，保证按拍取模的阶段从首拍对齐。 */
    private var sessionTickCount = 0L

    /** 最近一次对外广播的视频活跃值；null 表示会话内尚未发送过。 */
    private var lastBroadcastVideoActive: Boolean? = null
    private var lastBroadcastAmplitude = -1f

    /** 会话周期任务：每拍向悬浮球广播帧活动，每 5 拍刷新已录时长；流监视器独立采样。 */
    private val sessionTick = object : Runnable {
        override fun run() {
            val s = SessionStateStore.snapshot
            if (!s.isRunning) return
            sessionTickCount++
            if (sessionTickCount % RecorderService.SESSION_TICKS_PER_SECOND == 1L) {
                // 用单调时钟 elapsedRealtime 计时；会话起点未建立（启动回调未到）时按 0 秒上报。
                val startMs = sessionStartMsProvider()
                val elapsed = if (startMs > 0L) {
                    ((SystemClock.elapsedRealtime() - startMs) / 1000).coerceAtLeast(0)
                } else {
                    0L
                }
                SessionStateStore.reduce(SessionEvent.ElapsedTick(elapsed))
            }
            val sr = screenRecorderProvider()
            // 编码器产出全部轨道的输出格式后才算真正就绪：此时保存一定能取到轨道格式，
            // 否则会话启动最初几百毫秒内点保存会因"编码器格式不可用"失败或静默丢音轨。
            if (!captureReadyProvider() && sr != null &&
                capturePipelineReady(
                    videoFormatReady = sr.getVideoFormat() != null,
                    audioEnabled = sr.isAudioActive(),
                    audioFormatReady = sr.getAudioFormat() != null
                )
            ) {
                onCaptureReady()
            }
            if (sr != null && FloatingControlService.isRunning) {
                broadcastFrameActivity(sr)
                // 服务存活但窗口丢失（如悬浮层被系统回收）：每 5 拍尝试一次重建，重建失败即自行停服。
                if (!FloatingControlService.windowVisible &&
                    sessionTickCount % RecorderService.SESSION_TICKS_PER_SECOND == 0L
                ) {
                    try {
                        service.startService(
                            Intent(service, FloatingControlService::class.java).apply {
                                action = FloatingControlService.ACTION_UPDATE_SETTINGS
                            }
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Floating heal startService failed: ${e.message}")
                    }
                }
            }
            mainHandler.postDelayed(this, RecorderService.FRAME_BROADCAST_INTERVAL_MS)
        }
    }

    /** 复位“已发送”记录与拍数：新会话首拍必发、各阶段从首拍对齐。 */
    fun startTick() {
        lastBroadcastVideoActive = null
        lastBroadcastAmplitude = -1f
        sessionTickCount = 0L
        mainHandler.removeCallbacks(sessionTick)
        mainHandler.postDelayed(sessionTick, RecorderService.FRAME_BROADCAST_INTERVAL_MS)
    }

    fun stopTick() {
        mainHandler.removeCallbacks(sessionTick)
    }

    /** 向悬浮球广播帧活动；与上次值相同（含振幅小抖动）时不重发。 */
    private fun broadcastFrameActivity(sr: ScreenRecorder) {
        // 判据与通知同源（videoActiveProvider），此处不再自行计算：
        // 两处各自拼判据会让悬浮球指示灯与通知对「是否中断」给出相反结论。
        val videoActive = videoActiveProvider()
        val amplitude = sr.getAudioAmplitude()
        val changed = videoActive != lastBroadcastVideoActive ||
            kotlin.math.abs(amplitude - lastBroadcastAmplitude) > RecorderService.AMPLITUDE_SEND_EPSILON
        if (!changed) return
        lastBroadcastVideoActive = videoActive
        lastBroadcastAmplitude = amplitude
        service.sendBroadcast(
            Intent(RecorderService.ACTION_FRAME_ACTIVITY).apply {
                setPackage(service.packageName)
                putExtra(RecorderService.EXTRA_VIDEO_ACTIVE, videoActive)
                putExtra(RecorderService.EXTRA_AUDIO_AMPLITUDE, amplitude)
            }
        )
    }
}
