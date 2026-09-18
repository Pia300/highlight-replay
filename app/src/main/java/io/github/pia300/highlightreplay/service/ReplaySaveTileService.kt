package io.github.pia300.highlightreplay.service

import android.service.quicksettings.Tile
import android.util.Log
import io.github.pia300.highlightreplay.R
import io.github.pia300.highlightreplay.data.LanguagePrefs

/** 回放保存磁贴：录制会话进行中一键触发保存最近回放。 */
class ReplaySaveTileService : BaseTileService() {

    companion object {
        private const val TAG = "ReplaySaveTileService"
    }

    /** 处理磁贴点击：录制运行时向 RecorderService 发触发回放保存命令。 */
    override fun onClick() {
        super.onClick()

        if (!RecorderService.isRunning) {
            Log.d(TAG, "tile clicked but not recording, ignore")
            return
        }
        Log.d(TAG, "tile clicked, trigger replay")
        sendRecorderCommand(RecorderService.ACTION_TRIGGER_REPLAY)
    }

    /** 刷新磁贴状态与标签，反映录制与保存状态。 */
    override fun updateTileState() {
        val tile = qsTile ?: return

        tile.state = when {
            !RecorderService.isRunning -> Tile.STATE_UNAVAILABLE
            RecorderService.isSaving -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }

        tile.label = LanguagePrefs.string(
            this,
            if (RecorderService.isSaving) R.string.notification_saving_title
            else R.string.tile_save_replay
        )
        tile.updateTile()
    }
}
