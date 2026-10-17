package io.github.pia300.highlightreplay.ui.screens.settings

import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.pia300.highlightreplay.R
import io.github.pia300.highlightreplay.ui.components.SettingIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 悬浮窗设置日志标签。 */
private const val TAG = "FloatingSettingsComponents"

/** 悬浮窗权限状态行：进入 RESUMED 时在 IO 线程重新探测授权状态。 */
@Composable
internal fun FloatingPermissionRow() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val granted by produceState<Boolean?>(initialValue = null, lifecycleOwner) {
        val appContext = context.applicationContext
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            value = withContext(Dispatchers.IO) { Settings.canDrawOverlays(appContext) }
        }
    }
    val current = granted ?: return
    PermissionStatusRow(
        icon = SettingIcons.overlayPermission,
        label = stringResource(R.string.settings_floating_permission),
        grantedText = stringResource(R.string.settings_permission_granted),
        deniedText = stringResource(R.string.settings_permission_denied),
        granted = current,
        onClick = {
            try {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        "package:${context.packageName}".toUri()
                    )
                )
            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "Failed to open overlay permission settings: ${e.message}"
                )
            }
        }
    )
}
