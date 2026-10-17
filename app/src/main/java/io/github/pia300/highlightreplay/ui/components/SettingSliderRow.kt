package io.github.pia300.highlightreplay.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.pia300.highlightreplay.R

/** 连续数值设置行：减号、滑杆、加号与可点击的数值芯片。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingSliderRow(
    icon: ImageVector,
    label: String,
    valueRange: ClosedFloatingPointRange<Float>,
    initialValue: Float,
    formatValue: (Float) -> String,
    isPercent: Boolean,
    enabled: Boolean = true,
    onCommit: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var value by remember(initialValue) {
        mutableFloatStateOf(initialValue.coerceIn(valueRange.start, valueRange.endInclusive))
    }
    var showDialog by remember { mutableStateOf(false) }
    val displayText = remember(value) { formatValue(value) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                },
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                }
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 34.dp)
                .alpha(if (enabled) 1f else 0.38f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    val newValue = (value - 1f).coerceIn(valueRange.start, valueRange.endInclusive)
                    if (newValue != value) {
                        value = newValue
                        onCommit(newValue)
                    }
                },
                enabled = enabled
            ) {
                Icon(
                    imageVector = Icons.Outlined.Remove,
                    contentDescription = stringResource(R.string.settings_decrease),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
            Slider(
                value = value,
                onValueChange = { value = it },
                onValueChangeFinished = { if (value != initialValue) onCommit(value) },
                valueRange = valueRange,
                steps = ((valueRange.endInclusive - valueRange.start).toInt() - 1).coerceAtLeast(0),
                track = { sliderState ->
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        drawStopIndicator = null,
                        drawTick = { _, _ -> }
                    )
                },
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    val newValue = (value + 1f).coerceIn(valueRange.start, valueRange.endInclusive)
                    if (newValue != value) {
                        value = newValue
                        onCommit(newValue)
                    }
                },
                enabled = enabled
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.settings_increase),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            AssistChip(
                onClick = { showDialog = true },
                enabled = enabled,
                label = {
                    Text(
                        text = displayText,
                        style = MaterialTheme.typography.labelLarge
                    )
                },
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
            onConfirm = { newValue ->
                val committed = newValue.toFloat().coerceIn(valueRange.start, valueRange.endInclusive)
                if (committed != value) {
                    value = committed
                    onCommit(committed)
                }
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }
}

/** 精确数值输入对话框。 */
@Composable
private fun ValueInputDialog(
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
                onValueChange = { text ->
                    input = text.filter { it in '0'..'9' }.take(4)
                },
                label = {
                    Text(
                        stringResource(
                            if (isPercent) R.string.settings_range_percent else R.string.settings_range,
                            min,
                            max
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
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
