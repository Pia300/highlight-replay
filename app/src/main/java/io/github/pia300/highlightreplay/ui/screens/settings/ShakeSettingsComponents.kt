package io.github.pia300.highlightreplay.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.pia300.highlightreplay.R
import io.github.pia300.highlightreplay.data.RecorderSettings
import io.github.pia300.highlightreplay.ui.components.SettingCardSwitchRow
import io.github.pia300.highlightreplay.ui.components.SettingGroupCard

/**
 * 「触发力度」快捷档位：值与文案资源。
 *
 * 档位只是滑杆的跳转点，不是离散档位：拖到 43 时三个档位都不高亮。
 * 值必须落在 [RecorderSettings.SHAKE_STRENGTH_RANGE] 内且严格递增（见 ShakeStrengthPresetsTest）。
 */
internal val SHAKE_STRENGTH_PRESETS: List<Pair<Int, Int>> = listOf(
    25 to R.string.settings_shake_strength_low,
    50 to R.string.settings_shake_strength_medium,
    75 to R.string.settings_shake_strength_high
)

/** 摇一摇保存区块：标题、开关、触发力度滑杆与低/中/高快捷档位；力度改动即时下发到录制服务。 */
@Composable
internal fun ShakeToSaveSection(
    enabled: Boolean,
    strength: Int,
    onEnabledChange: (Boolean) -> Unit,
    onStrengthChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.settings_shake_to_save),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // 与 SwitchSettingRow 的标题位置一致：卡片左右各 16dp 内边距，此处再加 4dp。
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 4.dp)
        )
        SettingGroupCard(modifier = Modifier.padding(horizontal = 16.dp)) {
            SettingCardSwitchRow(
                description = stringResource(R.string.settings_shake_enabled),
                checked = enabled,
                onCheckedChange = onEnabledChange
            )
            SettingsDivider()
            SliderSettingRow(
                label = stringResource(R.string.settings_shake_strength),
                valueRange = RecorderSettings.SHAKE_STRENGTH_RANGE.start.toFloat()..
                    RecorderSettings.SHAKE_STRENGTH_RANGE.endInclusive.toFloat(),
                initialValue = strength.toFloat(),
                formatValue = { "${it.toInt()}%" },
                isPercent = true,
                onCommit = { onStrengthChange(it.toInt()) }
            )
            SettingsDivider()
            ShakeStrengthPresetRow(selected = strength, onSelect = onStrengthChange)
        }
        Text(
            text = stringResource(R.string.settings_shake_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 32.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)
        )
    }
}

/** 力度快捷档位行：点击跳到对应力度；已处于该档位时点击不再重复下发。 */
@Composable
private fun ShakeStrengthPresetRow(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)

            // 一组互斥快捷项，读屏按一组播报。
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SHAKE_STRENGTH_PRESETS.forEach { (preset, labelRes) ->
            val isSelected = preset == selected
            FilterChip(
                selected = isSelected,
                onClick = { if (!isSelected) onSelect(preset) },
                label = {
                    // 三个档位等宽，文字在按钮内居中。
                    Text(
                        text = stringResource(labelRes),
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
