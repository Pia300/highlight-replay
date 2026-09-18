package io.github.pia300.highlightreplay.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.pia300.highlightreplay.R

/** 权限说明弹窗宿主：按传入的开关状态显示通知与悬浮窗权限说明弹窗。 */
@Composable
internal fun ShellRationaleDialogs(
    showNotificationRationale: Boolean,
    onNotificationRationaleDismiss: () -> Unit,
    onNotificationRationaleConfirm: () -> Unit,
    showOverlayRationale: Boolean,
    onOverlayRationaleDismiss: () -> Unit,
    onOverlayRationaleConfirm: () -> Unit
) {
    // 通知权限被拒后的说明弹窗，引导前往系统设置开启。
    if (showNotificationRationale) {
        NotificationRationaleDialog(
            onDismiss = onNotificationRationaleDismiss,
            onConfirm = onNotificationRationaleConfirm
        )
    }

    // 悬浮窗权限被拒后的说明弹窗，确认后重新跳转授权页。
    if (showOverlayRationale) {
        OverlayRationaleDialog(
            onDismiss = onOverlayRationaleDismiss,
            onConfirm = onOverlayRationaleConfirm
        )
    }
}

/** 通知权限说明弹窗：标题、正文与确认/忽略按钮。 */
@Composable
private fun NotificationRationaleDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notification_permission_title)) },
        text = { Text(stringResource(R.string.notification_permission_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.notification_permission_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.notification_permission_dismiss))
            }
        }
    )
}

/** 悬浮窗权限说明弹窗：标题、正文与确认/忽略按钮。 */
@Composable
private fun OverlayRationaleDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.overlay_permission_title)) },
        text = { Text(stringResource(R.string.overlay_permission_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.overlay_permission_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.overlay_permission_dismiss))
            }
        }
    )
}
