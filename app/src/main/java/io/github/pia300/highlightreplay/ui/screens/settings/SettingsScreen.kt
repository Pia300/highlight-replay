package io.github.pia300.highlightreplay.ui.screens.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.pia300.highlightreplay.R
import io.github.pia300.highlightreplay.data.RecorderPrefs
import io.github.pia300.highlightreplay.ui.components.SettingIcons
import io.github.pia300.highlightreplay.ui.components.SettingSection
import io.github.pia300.highlightreplay.ui.components.SettingSliderRow
import io.github.pia300.highlightreplay.ui.components.SettingSwitchRow

/** 设置页入口：注入悬浮窗权限行与悬浮窗设置区块。 */
@Composable
fun SettingsScreen(
    themeMode: String,
    themeColor: String,
    onThemeModeChange: (String) -> Unit,
    onThemeColorChange: (String) -> Unit,
    onLanguageChange: () -> Unit,
    licenseOpen: Boolean,
    onLicenseOpen: () -> Unit,
    onLicenseClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel()
) {
    SettingsScreenContent(
        themeMode = themeMode,
        themeColor = themeColor,
        onThemeModeChange = onThemeModeChange,
        onThemeColorChange = onThemeColorChange,
        onLanguageChange = onLanguageChange,
        licenseOpen = licenseOpen,
        onLicenseOpen = onLicenseOpen,
        onLicenseClose = onLicenseClose,
        viewModel = viewModel,
        permissionExtraRows = remember {
            listOf<@Composable () -> Unit>({ FloatingPermissionRow() })
        },
        floatingSection = remember {
            val section: @Composable () -> Unit = { FloatingSettingsSection(viewModel) }
            section
        },
        modifier = modifier
    )
}

/** 悬浮窗设置区块：自动显示开关、大小/透明度滑杆与重置按钮。 */
@Composable
private fun FloatingSettingsSection(viewModel: SettingsViewModel) {
    val currentState by viewModel.state.collectAsStateWithLifecycle()

    SettingSection(
        icon = SettingIcons.floating,
        title = stringResource(R.string.settings_section_floating)
    ) {
        SettingSwitchRow(
            icon = SettingIcons.floatingVisibility,
            title = stringResource(R.string.settings_auto_show_floating_desc),
            checked = currentState.autoShowFloating,
            onCheckedChange = { checked ->
                viewModel.setBoolPref(RecorderPrefs.KEY_AUTO_SHOW_FLOATING, checked)
            }
        )
        SettingsDivider(insetStart = 52.dp)
        SettingSliderRow(
            icon = SettingIcons.floatingSize,
            label = stringResource(R.string.settings_floating_size),
            valueRange = RecorderPrefs.FLOATING_SIZE_MIN.toFloat()..
                RecorderPrefs.FLOATING_SIZE_MAX.toFloat(),
            initialValue = currentState.floatingSize.toFloat(),
            formatValue = { it.toInt().toString() },
            isPercent = false,
            onCommit = { value ->
                viewModel.setIntPref(RecorderPrefs.KEY_FLOATING_SIZE, value.toInt())
                viewModel.onFloatingSettingChanged()
            }
        )
        SettingsDivider(insetStart = 52.dp)
        SettingSliderRow(
            icon = SettingIcons.floatingOpacity,
            label = stringResource(R.string.settings_floating_opacity),
            valueRange = RecorderPrefs.FLOATING_OPACITY_MIN.toFloat()..
                RecorderPrefs.FLOATING_OPACITY_MAX.toFloat(),
            initialValue = currentState.floatingOpacity.toFloat(),
            formatValue = { "${it.toInt()}%" },
            isPercent = true,
            onCommit = { value ->
                viewModel.setIntPref(RecorderPrefs.KEY_FLOATING_OPACITY, value.toInt())
                viewModel.onFloatingSettingChanged()
            }
        )
        SettingsDivider(insetStart = 52.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            TextButton(onClick = { viewModel.resetFloating() }) {
                Icon(
                    imageVector = SettingIcons.reset,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.settings_reset_floating))
            }
        }
    }
}
