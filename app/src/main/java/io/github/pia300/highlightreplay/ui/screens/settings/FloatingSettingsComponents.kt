package io.github.pia300.highlightreplay.ui.screens.settings

import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.pia300.highlightreplay.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 悬浮窗设置组件日志标签。 */
private const val TAG = "FloatingSettingsComponents"

/** 带说明文字的开关设置行：整行可点击切换，右侧为 Switch。 */
@Composable
internal fun FloatingSwitchRow(
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(12.dp))
        // 整行已由 toggleable(role = Role.Switch) 提供开关语义，清除内嵌 Switch 语义避免无障碍树重复。
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.clearAndSetSemantics { }
        )
    }
}

/** 数值设置行：减号/滑块/加号调节，点击数值芯片弹出精确输入对话框。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SliderSettingRow(
    label: String,
    valueRange: ClosedFloatingPointRange<Float>,
    initialValue: Float,
    formatValue: (Float) -> String,
    onCommit: (Float) -> Unit,
    isPercent: Boolean,
    modifier: Modifier = Modifier
) {
    var value by remember(initialValue) {
        mutableFloatStateOf(initialValue.coerceIn(valueRange.start, valueRange.endInclusive))
    }
    var showDialog by remember { mutableStateOf(false) }
    // 保留单位后缀：尺寸与透明度两行都显示裸数字会分不清单位。
    val displayText = remember(value) { formatValue(value) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,

            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {

            IconButton(
                onClick = {
                    val new = (value - 1f).coerceIn(valueRange.start, valueRange.endInclusive)
                    if (new != value) {
                        value = new
                        onCommit(new)
                    }
                }
            ) {
                Icon(
                    Icons.Outlined.Remove,
                    contentDescription = stringResource(R.string.settings_decrease),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
            // 滑块拖动只更新内部状态，松手时才提交；值未变化不提交，避免无意义写入。
            Slider(
                value = value,
                onValueChange = { value = it },
                onValueChangeFinished = { if (value != initialValue) onCommit(value) },
                valueRange = valueRange,

                // 离散刻度数 = 范围长度 - 1，滑块按整数步进。
                steps = ((valueRange.endInclusive - valueRange.start).toInt() - 1).coerceAtLeast(0),
                // 轨道只绘制进度条本体与滑块：刻度点与端点指示点均不绘制。
                track = { sliderState ->
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        drawStopIndicator = null,
                        drawTick = { _, _ -> }
                    )
                },
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    val new = (value + 1f).coerceIn(valueRange.start, valueRange.endInclusive)
                    if (new != value) {
                        value = new
                        onCommit(new)
                    }
                }
            ) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.settings_increase),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(8.dp))

            AssistChip(
                onClick = { showDialog = true },
                label = { Text(displayText, style = MaterialTheme.typography.labelLarge) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            )
        }
    }

    if (showDialog) {
        ValueInputDialog(
            title = label,
            current = value.toInt(),
            min = valueRange.start.toInt(),
            max = valueRange.endInclusive.toInt(),
            isPercent = isPercent,
            onConfirm = { v ->
                val new = v.toFloat().coerceIn(valueRange.start, valueRange.endInclusive)
                if (new != value) {
                    value = new
                    onCommit(new)
                }
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }
}

/** 精确数值输入对话框：数字键盘仅允许数字输入，确认时解析并钳制到 [min, max]。 */
@Composable
internal fun ValueInputDialog(
    title: String,
    current: Int,
    min: Int,
    max: Int,
    isPercent: Boolean,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var input by remember { mutableStateOf(current.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = input,
                // 仅接受 ASCII 数字并限长，保证与确认时的 toIntOrNull 解析一致。
                onValueChange = { text ->
                    input = text.filter { it in '0'..'9' }.take(4)
                },
                label = {

                    Text(
                        stringResource(
                            if (isPercent) R.string.settings_range_percent else R.string.settings_range,
                            min, max
                        )
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(input.toIntOrNull()?.coerceIn(min, max) ?: current)
            }) {
                Text(stringResource(R.string.common_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

/** 悬浮窗权限状态行：权限探测在 IO 线程完成，点击跳转系统设置页，回到前台时自动复查。 */
@Composable
internal fun FloatingPermissionRow() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // canDrawOverlays 走 AppOps Binder，置于组合期会阻塞主线程；改在 IO 线程探测。
    // 初始值取空以区分"尚未探测"，避免用 false 误报为未授权。
    val granted by produceState<Boolean?>(initialValue = null, lifecycleOwner) {
        val appContext = context.applicationContext
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            value = withContext(Dispatchers.IO) { Settings.canDrawOverlays(appContext) }
        }
    }
    val current = granted ?: return
    PermissionStatusRow(
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

                // 部分设备无此页面：记日志后忽略，不影响其余设置行。
                Log.w(TAG, "Failed to open overlay permission settings: ${e.message}")
            }
        }
    )
}
