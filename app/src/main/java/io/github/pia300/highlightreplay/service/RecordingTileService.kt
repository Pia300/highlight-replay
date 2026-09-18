package io.github.pia300.highlightreplay.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.util.Log
import io.github.pia300.highlightreplay.R
import io.github.pia300.highlightreplay.RecordingStartActivity
import io.github.pia300.highlightreplay.data.LanguagePrefs

/** 快捷设置磁贴：点击即开始或停止屏幕录制。 */
class RecordingTileService : BaseTileService() {

    companion object {
        private const val TAG = "RecordingTileService"

        /** 拉起授权页 PendingIntent 的 requestCode（唯一性约定，非被校验的不变量）。 */
        private const val REQUEST_CODE_LAUNCH_CONSENT = 2001

        /** “启动中”兜底复位延迟：授权取消等路径不会广播刷新，超时后按真实状态复位。 */
        private const val STARTING_RESET_DELAY_MS = 10_000L
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    /** 启动中状态的兜底复位：会话未真正开始则按真实状态刷新磁贴。 */
    private val resetStartingTileRunnable = Runnable {
        if (!RecorderService.isRunning) updateTileState()
    }

    /** 处理磁贴点击：录制中发停止广播，否则拉起授权界面开始录制。 */
    override fun onClick() {
        super.onClick()
        Log.d(TAG, "tile clicked, isRunning=${RecorderService.isRunning}")
        when {
            RecorderService.isRunning -> sendRecorderCommand(RecorderService.ACTION_STOP)

            // 锁屏下授权页无法显示在锁屏之上：先请求解锁，解锁成功后再发起授权。
            // 用户放弃解锁时由“启动中”兜底复位把磁贴恢复成真实状态。
            isLocked -> {
                markStartingAndScheduleReset()
                unlockAndRun { launchConsent() }
            }

            else -> {
                markStartingAndScheduleReset()
                launchConsent()
            }
        }
    }

    /** 置“启动中”防重复点击，并安排兜底复位（授权取消等路径不会广播刷新）。 */
    private fun markStartingAndScheduleReset() {
        setTileState(Tile.STATE_ACTIVE, LanguagePrefs.string(this, R.string.tile_starting))
        mainHandler.removeCallbacks(resetStartingTileRunnable)
        mainHandler.postDelayed(resetStartingTileRunnable, STARTING_RESET_DELAY_MS)
    }

    /** 收起面板并拉起授权页。 */
    private fun launchConsent() {
        val intent = Intent(this, RecordingStartActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {

            // Android 14+ 要求以 PendingIntent 形式收起面板并拉起授权页。
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pending = PendingIntent.getActivity(
                    this, REQUEST_CODE_LAUNCH_CONSENT, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pending)
            } else {
                startActivityAndCollapseLegacy(intent)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to launch consent activity: ${e.message}")

            // 拉起失败即恢复真实状态，避免停留在“启动中”。
            updateTileState()
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(resetStartingTileRunnable)
        super.onDestroy()
    }

    /** 旧版兼容入口：用 Intent 收起面板并拉起授权界面。 */
    @SuppressLint("Deprecation", "StartActivityAndCollapseDeprecated")
    private fun startActivityAndCollapseLegacy(intent: Intent) {

        startActivityAndCollapse(intent)
    }

    /** 按当前录制状态刷新磁贴状态与标签文字。 */
    override fun updateTileState() {
        val running = RecorderService.isRunning
        setTileState(
            if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE,

            LanguagePrefs.string(
                this,
                if (running) R.string.tile_recording
                else R.string.tile_start_recording
            )
        )
    }

    /** 写入磁贴状态与标签并请求系统刷新。 */
    private fun setTileState(state: Int, label: String) {
        // 磁贴未绑定时直接返回，待下次可见再刷新。
        val tile = qsTile ?: return
        tile.state = state
        tile.label = label
        tile.updateTile()
    }
}
