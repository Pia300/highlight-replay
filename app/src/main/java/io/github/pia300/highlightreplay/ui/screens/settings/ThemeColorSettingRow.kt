package io.github.pia300.highlightreplay.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import io.github.pia300.highlightreplay.R
import io.github.pia300.highlightreplay.data.ThemePrefs
import io.github.pia300.highlightreplay.ui.components.SettingChoiceRow
import io.github.pia300.highlightreplay.ui.components.SettingIcons
import io.github.pia300.highlightreplay.ui.components.SettingSwatchRow
import io.github.pia300.highlightreplay.ui.components.ThemeSwatch
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

/** 主题色设置行：横向色板展示动态取色与预设配色。 */
@Composable
internal fun ThemeColorSettingRow(
    selected: String,
    dynamicSupported: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val dark = LocalDarkTheme.current
    val dynamicSwatch = if (dynamicSupported) {
        ThemeSwatch(
            value = ThemePrefs.COLOR_DYNAMIC,
            label = stringResource(R.string.settings_color_dynamic),
            color = Color.Transparent,
            gradient = DynamicSwatchColors
        )
    } else {
        null
    }
    val options = listOfNotNull(
        dynamicSwatch,
        ThemeSwatch(
            value = ThemePrefs.COLOR_BLUE,
            label = stringResource(R.string.settings_color_blue),
            color = if (dark) DarkStaticColors.primary else LightStaticColors.primary
        ),
        ThemeSwatch(
            value = ThemePrefs.COLOR_GREEN,
            label = stringResource(R.string.settings_color_green),
            color = if (dark) DarkGreenColors.primary else LightGreenColors.primary
        ),
        ThemeSwatch(
            value = ThemePrefs.COLOR_PURPLE,
            label = stringResource(R.string.settings_color_purple),
            color = if (dark) DarkPurpleColors.primary else LightPurpleColors.primary
        ),
        ThemeSwatch(
            value = ThemePrefs.COLOR_ORANGE,
            label = stringResource(R.string.settings_color_orange),
            color = if (dark) DarkOrangeColors.primary else LightOrangeColors.primary
        )
    )
    val effectiveSelected = if (!dynamicSupported && selected == ThemePrefs.COLOR_DYNAMIC) {
        ThemePrefs.COLOR_BLUE
    } else {
        selected
    }

    SettingChoiceRow(
        icon = SettingIcons.themeColor,
        title = stringResource(R.string.settings_theme_color),
        modifier = modifier
    ) {
        SettingSwatchRow(
            options = options,
            selected = effectiveSelected,
            onSelect = onSelect
        )
    }
}
