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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.pia300.highlightreplay.R
import io.github.pia300.highlightreplay.ui.components.SettingGroupCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 权限行模块日志标签。 */
private const val TAG = "SettingsPermissionRows"

/** 权限设置组：附加行、通知权限行（仅 13+）、电池豁免行收进同一卡片，行间统一插分隔线。 */
@Composable
internal fun PermissionGroup(
    modifier: Modifier = Modifier,
    extraRows: List<@Composable () -> Unit> = emptyList()
) {
    SettingGroupCard(modifier = modifier) {
        val rows: List<@Composable () -> Unit> = buildList {
            addAll(extraRows)
            // 旧版外部存储权限行仅 Android 9（API 28）及以下展示：该范围查询媒体库需要该权限。
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add { LegacyStoragePermissionRow() }
            }
            // 通知权限行仅 Android 13（API 33）及以上展示。
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add { NotificationPermissionRow() }
            }
            add { BatteryOptimizationRow() }
        }
        for (i in rows.indices) {
            if (i > 0) SettingsDivider()
            rows[i]()
        }
    }
}

/**
 * 权限授予状态：组合时在调用线程读取一次作为初值，此后每次进入 RESUMED 都在 IO 线程重新读取，
 * 使从系统设置页返回后的授权变化立即反映到界面。
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

/** 权限状态行：标题、授予/未授予状态文本，以及跳转系统设置页的入口图标。授权状态由调用方持有。 */
@Composable
internal fun PermissionStatusRow(
    label: String,
    grantedText: String,
    deniedText: String,
    granted: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        supportingContent = {
            Text(
                text = if (granted) grantedText else deniedText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Outlined.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    )
}

/** 旧版外部存储权限行（API 28 及以下）：查询媒体库与写入公共目录需要该权限。 */
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

/** 通知权限设置行：跳转系统通知设置页并实时显示授予状态。 */
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

/** 电池优化白名单设置行：防止后台录制被系统限制。 */
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
        label = stringResource(R.string.settings_battery_optimization),
        grantedText = stringResource(R.string.settings_battery_optimization_granted),
        deniedText = stringResource(R.string.settings_battery_optimization_denied),
        granted = granted,
        onClick = {
            try {
                // 首选直接弹出"允许忽略电池优化"系统对话框。
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = "package:${context.packageName}".toUri()
                    }
                )
            } catch (e: Exception) {
                // 直接申请对话框在部分设备/策略下不可用：退回全局电池优化设置列表并记日志。
                Log.w(TAG, "Request-ignore-battery-optimizations dialog failed: ${e.message}")
                try {
                    context.startActivity(
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    )
                } catch (e2: Exception) {
                    // 设置页也不可用时放弃，连同对话框失败一并记日志。
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
