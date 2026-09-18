package io.github.pia300.highlightreplay.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.service.quicksettings.TileService
import android.util.Log

/** 磁贴服务基类：动态注册包级磁贴更新广播并转发到 updateTileState()；非导出标志仅 Android 13+ 可用。 */
abstract class BaseTileService : TileService() {

    abstract fun updateTileState()

    /** 向录制服务发送命令；startService 失败仅记日志。 */
    protected fun sendRecorderCommand(action: String) {
        try {
            startService(Intent(this, RecorderService::class.java).apply { this.action = action })
        } catch (e: Exception) {
            Log.w(TAG, "startService failed for $action: ${e.message}")
        }
    }

    private val tileUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == RecorderService.ACTION_TILE_UPDATE) {
                updateTileState()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(
                    tileUpdateReceiver,
                    IntentFilter(RecorderService.ACTION_TILE_UPDATE),
                    RECEIVER_NOT_EXPORTED
                )
            } else {
                // 旧版本无导出标志参数：以签名级权限约束发送方，防第三方伪造磁贴刷新广播。
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(
                    tileUpdateReceiver,
                    IntentFilter(RecorderService.ACTION_TILE_UPDATE),
                    RecorderService.INTERNAL_BROADCAST_PERMISSION,
                    null
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register tile-update receiver: ${e.message}")
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(tileUpdateReceiver)
        } catch (_: Exception) {
        }
    }

    private companion object {
        const val TAG = "BaseTileService"
    }
}
