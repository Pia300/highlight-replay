package io.github.pia300.highlightreplay.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.pia300.highlightreplay.R
import io.github.pia300.highlightreplay.data.RecorderPrefs
import io.github.pia300.highlightreplay.ui.components.SectionHeader
import io.github.pia300.highlightreplay.ui.components.SettingGroupCard

/** 设置页入口：包装共用内容，注入悬浮窗权限行与“悬浮窗设置”区块。 */
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
        // remember：避免每次重组新建 List/lambda 导致 SettingsScreenContent 无法跳过重组。
        permissionExtraRows = remember { listOf<@Composable () -> Unit>({ FloatingPermissionRow() }) },
        // 显式标注 @Composable 类型：否则 remember 内部无法推断嵌套 lambda 是 composable。
        floatingSection = remember {
            val section: @Composable () -> Unit = { FloatingSettingsSection(viewModel) }
            section
        },
        modifier = modifier
    )
}

/** 悬浮窗设置区块：自动显示悬浮球开关、大小/透明度滑杆与重置按钮；外观修改即时通知悬浮球应用。 */
@Composable
private fun FloatingSettingsSection(viewModel: SettingsViewModel) {

    val currentState by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.settings_section_floating))
        SettingGroupCard(
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {

            // 会话开始时是否自动显示悬浮球（显隐规则见 RecorderService.ensureFloatingService）。
            FloatingSwitchRow(
                description = stringResource(R.string.settings_auto_show_floating_desc),
                checked = currentState.autoShowFloating,
                onCheckedChange = { checked ->
                    viewModel.setBoolPref(RecorderPrefs.KEY_AUTO_SHOW_FLOATING, checked)
                }
            )
            SettingsDivider()

            SliderSettingRow(
                label = stringResource(R.string.settings_floating_size),
                valueRange = RecorderPrefs.FLOATING_SIZE_MIN.toFloat()..RecorderPrefs.FLOATING_SIZE_MAX.toFloat(),
                initialValue = currentState.floatingSize.toFloat(),
                formatValue = { "${it.toInt()}" },
                isPercent = false,
                onCommit = { v ->
                    viewModel.setIntPref(RecorderPrefs.KEY_FLOATING_SIZE, v.toInt())
                    viewModel.onFloatingSettingChanged()
                }
            )
            SettingsDivider()

            SliderSettingRow(
                label = stringResource(R.string.settings_floating_opacity),
                valueRange = RecorderPrefs.FLOATING_OPACITY_MIN.toFloat()..RecorderPrefs.FLOATING_OPACITY_MAX.toFloat(),
                initialValue = currentState.floatingOpacity.toFloat(),
                formatValue = { "${it.toInt()}%" },
                isPercent = true,
                onCommit = { v ->
                    viewModel.setIntPref(RecorderPrefs.KEY_FLOATING_OPACITY, v.toInt())
                    viewModel.onFloatingSettingChanged()
                }
            )
            SettingsDivider()

            // 重置按钮：恢复悬浮球默认外观与位置。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { viewModel.resetFloating() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Outlined.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_reset_floating))
                }
            }
        }
    }
}
