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
import androidx.compose.ui.unit.dp
import io.github.pia300.highlightreplay.R
import io.github.pia300.highlightreplay.data.RecorderSettings
import io.github.pia300.highlightreplay.ui.components.SettingCardSwitchRow
import io.github.pia300.highlightreplay.ui.components.SettingGroupCard

/**
 * 「触发力度」快捷档位：值与文案资源。低/中/高只是滑杆的跳转点，不是离散档位——
 * 用户拖到 43 也是合法值，此时三个档位都不高亮。
 *
 * 档位值必须落在 [RecorderSettings.SHAKE_STRENGTH_RANGE] 内且严格递增（见 ShakeStrengthPresetsTest）。
 */
internal val SHAKE_STRENGTH_PRESETS: List<Pair<Int, Int>> = listOf(
    25 to R.string.settings_shake_strength_low,
    50 to R.string.settings_shake_strength_medium,
    75 to R.string.settings_shake_strength_high
)

/**
 * 摇一摇保存区块：开关、触发力度滑杆与低/中/高快捷档位。
 *
 * 力度改动即时下发到录制服务（录制中立刻按新力度判定），故本区块不参与「下次生效」的提示。
 * 设备无加速度计时上层不渲染本区块（见 [SettingsScreenContent]）。
 */
@Composable
internal fun ShakeToSaveSection(
    enabled: Boolean,
    strength: Int,
    onEnabledChange: (Boolean) -> Unit,
    onStrengthChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
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
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)
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

            // 语义上是一组互斥快捷项，读屏会播报为一组选择。
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SHAKE_STRENGTH_PRESETS.forEach { (preset, labelRes) ->
            val isSelected = preset == selected
            FilterChip(
                selected = isSelected,
                onClick = { if (!isSelected) onSelect(preset) },
                label = {
                    Text(
                        text = stringResource(labelRes),
                        style = MaterialTheme.typography.labelLarge
                    )
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
