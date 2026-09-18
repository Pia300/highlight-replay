package io.github.pia300.highlightreplay.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import io.github.pia300.highlightreplay.R
import io.github.pia300.highlightreplay.data.ThemePrefs
import io.github.pia300.highlightreplay.ui.components.SettingGroupCard
import io.github.pia300.highlightreplay.ui.theme.DarkGreenColors
import io.github.pia300.highlightreplay.ui.theme.DarkOrangeColors
import io.github.pia300.highlightreplay.ui.theme.DarkPurpleColors
import io.github.pia300.highlightreplay.ui.theme.DarkStaticColors
import io.github.pia300.highlightreplay.ui.theme.DynamicSwatchColors
import io.github.pia300.highlightreplay.ui.theme.LightGreenColors
import io.github.pia300.highlightreplay.ui.theme.LightOrangeColors
import io.github.pia300.highlightreplay.ui.theme.LightPurpleColors
import io.github.pia300.highlightreplay.ui.theme.LightStaticColors
import io.github.pia300.highlightreplay.ui.theme.LocalDarkTheme

/** 主题色设置行：单选列表展示动态取色与蓝/绿/紫/橙预设主题色。 */
@Composable
internal fun ThemeColorSettingRow(
    selected: String,
    dynamicSupported: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .selectableGroup()
    ) {
        Text(
            text = stringResource(R.string.settings_theme_color),

            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                start = 4.dp,
                end = 4.dp,
                top = 4.dp,
                bottom = 4.dp
            )
        )
        SettingGroupCard {
            if (dynamicSupported) {
                ThemeColorOption(
                    label = stringResource(R.string.settings_color_dynamic),
                    value = ThemePrefs.COLOR_DYNAMIC,
                    selected = selected == ThemePrefs.COLOR_DYNAMIC,
                    swatch = {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(
                                    // 动态取色以线性渐变色块示意。
                                    Brush.linearGradient(DynamicSwatchColors)
                                )
                        )
                    },
                    onSelect = onSelect
                )
                SettingsDivider()
            }

            // 不支持动态取色时“动态”取值回退到蓝色，保证总有选项处于选中态。
            val blueSelected = selected == ThemePrefs.COLOR_BLUE ||
                    (!dynamicSupported && selected == ThemePrefs.COLOR_DYNAMIC)
            ThemeColorOption(
                label = stringResource(R.string.settings_color_blue),
                value = ThemePrefs.COLOR_BLUE,
                selected = blueSelected,
                swatch = {

                    ColorSwatch(
                        color = if (LocalDarkTheme.current)
                            DarkStaticColors.primary else LightStaticColors.primary,
                        selected = blueSelected
                    )
                },
                onSelect = onSelect
            )
            SettingsDivider()
            ThemeColorOption(
                label = stringResource(R.string.settings_color_green),
                value = ThemePrefs.COLOR_GREEN,
                selected = selected == ThemePrefs.COLOR_GREEN,
                swatch = {
                    ColorSwatch(
                        color = if (LocalDarkTheme.current)
                            DarkGreenColors.primary else LightGreenColors.primary,
                        selected = selected == ThemePrefs.COLOR_GREEN
                    )
                },
                onSelect = onSelect
            )
            SettingsDivider()
            ThemeColorOption(
                label = stringResource(R.string.settings_color_purple),
                value = ThemePrefs.COLOR_PURPLE,
                selected = selected == ThemePrefs.COLOR_PURPLE,
                swatch = {
                    ColorSwatch(
                        color = if (LocalDarkTheme.current)
                            DarkPurpleColors.primary else LightPurpleColors.primary,
                        selected = selected == ThemePrefs.COLOR_PURPLE
                    )
                },
                onSelect = onSelect
            )
            SettingsDivider()
            ThemeColorOption(
                label = stringResource(R.string.settings_color_orange),
                value = ThemePrefs.COLOR_ORANGE,
                selected = selected == ThemePrefs.COLOR_ORANGE,
                swatch = {
                    ColorSwatch(
                        color = if (LocalDarkTheme.current)
                            DarkOrangeColors.primary else LightOrangeColors.primary,
                        selected = selected == ThemePrefs.COLOR_ORANGE
                    )
                },
                onSelect = onSelect
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}

/** 单个主题色选项：色块加单选按钮，整行可点击。 */
@Composable
internal fun ThemeColorOption(
    label: String,
    value: String,
    selected: Boolean,
    swatch: @Composable () -> Unit,
    onSelect: (String) -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface
            )
        },
        leadingContent = swatch,
        trailingContent = {
            RadioButton(
                selected = selected,
                // 点击已由整行 selectable 处理，清除单选按钮自身点击语义避免双重响应。
                onClick = null,
                modifier = Modifier.clearAndSetSemantics { }
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent
        ),
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = { onSelect(value) }
            )
    )
}

/** 圆形主题色色块，选中时中心显示对勾图标。 */
@Composable
internal fun ColorSwatch(color: Color, selected: Boolean = false) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {

        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                // 按色块亮度选择对勾颜色，保证对比度可读。
                tint = if (color.luminance() > 0.5f) Color(0xFF1A1C1E) else Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
