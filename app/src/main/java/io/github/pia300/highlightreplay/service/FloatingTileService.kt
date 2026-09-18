package io.github.pia300.highlightreplay.service

import android.service.quicksettings.Tile
import android.util.Log
import io.github.pia300.highlightreplay.R
import io.github.pia300.highlightreplay.data.LanguagePrefs

/** 悬浮窗开关磁贴：录制会话中一键显示/隐藏悬浮窗。 */
class FloatingTileService : BaseTileService() {

    companion object {
        private const val TAG = "FloatingTileService"
    }

    /** 点击磁贴：录制中向录制服务发送显隐切换命令（与通知按钮同路径）。 */
    override fun onClick() {
        super.onClick()
        if (!RecorderService.isRunning) {
            Log.d(TAG, "tile clicked but not recording, ignore")
            return
        }
        sendRecorderCommand(RecorderService.ACTION_TOGGLE_FLOATING)
    }

    /** 刷新磁贴状态与文案：未录制不可用；录制中按悬浮窗实际可见性显示“隐藏/显示”。 */
    override fun updateTileState() {
        val tile = qsTile ?: return
        when {
            !RecorderService.isRunning -> {
                tile.state = Tile.STATE_UNAVAILABLE
                tile.label = LanguagePrefs.string(this, R.string.tile_floating)
            }
            FloatingControlService.isRunning -> {
                tile.state = Tile.STATE_ACTIVE
                tile.label = LanguagePrefs.string(this, R.string.tile_hide_floating)
            }
            else -> {
                tile.state = Tile.STATE_INACTIVE
                tile.label = LanguagePrefs.string(this, R.string.tile_show_floating)
            }
        }
        tile.updateTile()
    }
}
