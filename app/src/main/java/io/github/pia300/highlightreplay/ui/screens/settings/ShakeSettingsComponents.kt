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
import io.github.pia300.highlightreplay.ui.components.SettingIcons
import io.github.pia300.highlightreplay.ui.components.SettingSliderRow
import io.github.pia300.highlightreplay.ui.components.SettingSwitchRow

/** 触发力度快捷档位：值与文案资源。 */
internal val SHAKE_STRENGTH_PRESETS: List<Pair<Int, Int>> = listOf(
    25 to R.string.settings_shake_strength_low,
    50 to R.string.settings_shake_strength_medium,
    75 to R.string.settings_shake_strength_high
)

/** 摇一摇保存区块：开关、力度滑杆与低/中/高快捷档位。 */
@Composable
internal fun ShakeToSaveSection(
    enabled: Boolean,
    strength: Int,
    onEnabledChange: (Boolean) -> Unit,
    onStrengthChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SettingSwitchRow(
            icon = SettingIcons.shake,
            title = stringResource(R.string.settings_shake_to_save),
            checked = enabled,
            onCheckedChange = onEnabledChange,
            hint = stringResource(R.string.settings_shake_hint)
        )
        SettingsDivider(insetStart = 50.dp)
        SettingSliderRow(
            icon = SettingIcons.shake,
            label = stringResource(R.string.settings_shake_strength),
            valueRange = RecorderSettings.SHAKE_STRENGTH_RANGE.start.toFloat()..
                RecorderSettings.SHAKE_STRENGTH_RANGE.endInclusive.toFloat(),
            initialValue = strength.toFloat(),
            formatValue = { "${it.toInt()}%" },
            isPercent = true,
            enabled = enabled,
            onCommit = { onStrengthChange(it.toInt()) }
        )
        SettingsDivider(insetStart = 50.dp)
        ShakeStrengthPresetRow(
            selected = strength,
            enabled = enabled,
            onSelect = onStrengthChange
        )
    }
}

/** 力度快捷档位行：点击跳到对应力度。 */
@Composable
private fun ShakeStrengthPresetRow(
    selected: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SHAKE_STRENGTH_PRESETS.forEach { (preset, labelRes) ->
            val isSelected = preset == selected
            FilterChip(
                selected = isSelected,
                onClick = { if (!isSelected) onSelect(preset) },
                enabled = enabled,
                label = {
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
