package io.github.pia300.highlightreplay.ui.screens.settings

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.pia300.highlightreplay.R
import io.github.pia300.highlightreplay.ui.components.SettingIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 权限行模块日志标签。 */
private const val TAG = "SettingsPermissionRows"

/** 权限设置组：附加行与系统权限行依次排列，行间插入分隔线。 */
@Composable
internal fun ColumnScope.PermissionGroup(
    extraRows: List<@Composable () -> Unit> = emptyList()
) {
    val rows: List<@Composable () -> Unit> = buildList {
        addAll(extraRows)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            add { LegacyStoragePermissionRow() }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add { NotificationPermissionRow() }
        }
        add { BatteryOptimizationRow() }
    }
    rows.forEachIndexed { index, row ->
        if (index > 0) SettingsDivider(insetStart = 52.dp)
        row()
    }
}

/**
 * 权限授予状态：组合时读取一次作为初值，此后每次进入 RESUMED 都在 IO 线程重新读取。
 */
@Composable
internal fun rememberPermissionGranted(check: () -> Boolean): State<Boolean> {
    val lifecycleOwner = LocalLifecycleOwner.current
    return produceState(initialValue = check(), lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            value = withContext(Dispatchers.IO) { check() }
        }
    }
}

/** 权限状态行：前置图标、标题、状态图标与跳转入口。 */
@Composable
internal fun PermissionStatusRow(
    icon: ImageVector,
    label: String,
    grantedText: String,
    deniedText: String,
    granted: Boolean,
    onClick: () -> Unit
) {
    val statusIcon = if (granted) SettingIcons.granted else SettingIcons.denied
    val statusColor = if (granted) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (granted) grantedText else deniedText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (granted) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
    }
}

/** 旧版外部存储权限行（API 28 及以下）。 */
@Composable
internal fun LegacyStoragePermissionRow() {
    val context = LocalContext.current
    val granted by rememberPermissionGranted {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }
    PermissionStatusRow(
        icon = SettingIcons.storagePermission,
        label = stringResource(R.string.settings_storage_permission),
        grantedText = stringResource(R.string.settings_permission_granted),
        deniedText = stringResource(R.string.settings_permission_denied),
        granted = granted,
        onClick = {
            launcher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    )
}

/** 通知权限设置行。 */
@SuppressLint("InlinedApi")
@Composable
internal fun NotificationPermissionRow() {
    val context = LocalContext.current
    val granted by rememberPermissionGranted {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
    PermissionStatusRow(
        icon = SettingIcons.notificationPermission,
        label = stringResource(R.string.settings_notification_permission),
        grantedText = stringResource(R.string.settings_notification_granted),
        deniedText = stringResource(R.string.settings_notification_denied),
        granted = granted,
        onClick = {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                )
            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "Failed to open the notification settings page: ${e.message}"
                )
            }
        }
    )
}

/** 电池优化白名单设置行。 */
@SuppressLint("BatteryLife")
@Composable
internal fun BatteryOptimizationRow() {
    val context = LocalContext.current
    val powerManager = remember {
        context.getSystemService(Context.POWER_SERVICE) as PowerManager
    }
    val granted by rememberPermissionGranted {
        powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }
    PermissionStatusRow(
        icon = SettingIcons.batteryPermission,
        label = stringResource(R.string.settings_battery_optimization),
        grantedText = stringResource(R.string.settings_battery_optimization_granted),
        deniedText = stringResource(R.string.settings_battery_optimization_denied),
        granted = granted,
        onClick = {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = "package:${context.packageName}".toUri()
                    }
                )
            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "Request-ignore-battery-optimizations dialog failed: ${e.message}"
                )
                try {
                    context.startActivity(
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    )
                } catch (e2: Exception) {
                    Log.w(
                        TAG,
                        "Battery optimization exemption unavailable: dialog=${e.message}, " +
                            "settings=${e2.message}"
                    )
                }
            }
        }
    )
}
