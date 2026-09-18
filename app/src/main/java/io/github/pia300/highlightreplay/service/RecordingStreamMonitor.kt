package io.github.pia300.highlightreplay.service

import android.os.Handler
import android.util.Log

/** 每 100ms 采样音视频活性，仅状态变化或首次采样时回调；stop() 复位采样状态，下一会话重新首采纠正。 */
class RecordingStreamMonitor(
    private val mainHandler: Handler,
    private val videoActiveProvider: () -> Boolean,
    private val audioActiveProvider: () -> Boolean,
    private val onStateChanged: (videoActive: Boolean, audioActive: Boolean) -> Unit
) {

    private companion object {
        const val TAG = "RecordingStreamMonitor"
        const val SAMPLE_INTERVAL_MS = 100L
    }

    private var started = false
    private var sampledOnce = false
    private var lastVideoActive = false
    private var lastAudioActive = false

    /** 是否已采样过（通知重建时据此套用真实流状态）。 */
    fun hasSampled(): Boolean = sampledOnce

    fun videoActive(): Boolean = lastVideoActive

    fun audioActive(): Boolean = lastAudioActive

    private val sampleTask = object : Runnable {
        override fun run() {
            val video = videoActiveProvider()
            val audio = audioActiveProvider()
            val first = !sampledOnce
            if (first || video != lastVideoActive || audio != lastAudioActive) {
                sampledOnce = true
                lastVideoActive = video
                lastAudioActive = audio
                try {
                    onStateChanged(video, audio)
                } catch (e: Exception) {
                    Log.e(TAG, "Stream-state callback exception", e)
                }
            }
            if (started) mainHandler.postDelayed(this, SAMPLE_INTERVAL_MS)
        }
    }

    /** 开始采样（幂等）。 */
    fun start() {
        if (started) return
        started = true
        mainHandler.post(sampleTask)
    }

    /** 停止采样（幂等），并复位采样状态供下一会话重新首采纠正。 */
    fun stop() {
        if (!started) return
        started = false
        sampledOnce = false
        lastVideoActive = false
        lastAudioActive = false
        mainHandler.removeCallbacks(sampleTask)
    }
}
