package io.github.pia300.highlightreplay.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.pia300.highlightreplay.ui.screens.control.ControlScreen
import io.github.pia300.highlightreplay.ui.screens.control.ControlUiState
import io.github.pia300.highlightreplay.ui.screens.history.HistoryScreen
import io.github.pia300.highlightreplay.ui.screens.settings.SettingsScreen

/** 导航分发：横屏为左侧导航栏加内容区，竖屏直接渲染内容。 */
@Composable
internal fun MainNavigation(
    isLandscape: Boolean,
    currentTab: Tab,
    onTabSelected: (Tab) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit
) {
    // 横屏用左侧导航栏，与内容区水平排列。
    if (isLandscape) {
        Row(modifier = modifier.fillMaxSize()) {
            NavigationRail(
                modifier = Modifier.fillMaxHeight(),
                windowInsets = WindowInsets(0, 0, 0, 0)
            ) {

                Column(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Tab.entries.forEach { t ->
                        NavigationRailItem(
                            selected = currentTab == t,
                            onClick = { onTabSelected(t) },
                            icon = {
                                val label = stringResource(t.labelRes)
                                Icon(
                                    t.icon,
                                    contentDescription = label,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = {
                                Text(
                                    stringResource(t.labelRes),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        )
                    }
                }
            }
            content(Modifier.weight(1f))
        }
    } else {
        content(modifier)
    }
}

/** 竖屏底部导航栏：按页签枚举渲染导航项。 */
@Composable
internal fun PortraitNavigationBar(
    currentTab: Tab,
    onTabSelected: (Tab) -> Unit
) {
    NavigationBar {
        Tab.entries.forEach { t ->
            NavigationBarItem(
                selected = currentTab == t,
                onClick = { onTabSelected(t) },
                icon = {
                    val label = stringResource(t.labelRes)
                    Icon(
                        t.icon,
                        contentDescription = label,
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = {
                    Text(
                        stringResource(t.labelRes)

                    )
                }
            )
        }
    }
}

/** 按当前页签分发到控制、历史或设置页面。 */
@Composable
internal fun TabContent(
    currentTab: Tab,
    onTabSelected: (Tab) -> Unit,
    licenseOpen: Boolean,
    onLicenseOpen: () -> Unit,
    onLicenseClose: () -> Unit,
    onHistorySelectionChange: (Boolean) -> Unit,
    uiState: () -> ControlUiState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onSaveReplay: () -> Unit,
    themeMode: String,
    themeColor: String,
    onThemeModeChange: (String) -> Unit,
    onThemeColorChange: (String) -> Unit,
    onLanguageChange: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 每个页签的状态由容器托管：切换页签会使该分支离开组合，无容器时其 rememberSaveable 值无处保存。
    val tabStateHolder = rememberSaveableStateHolder()

    // 非控制页按返回键回到控制页，而非退出应用。
    BackHandler(enabled = currentTab != Tab.CONTROL) {
        onTabSelected(Tab.CONTROL)
    }

    when (currentTab) {
        Tab.CONTROL -> tabStateHolder.SaveableStateProvider(Tab.CONTROL) {
            ControlScreen(
                uiState = uiState(),
                onStartRecording = onStartRecording,
                onStopRecording = onStopRecording,
                onSaveReplay = onSaveReplay,
                modifier = modifier
            )
        }

        Tab.HISTORY -> tabStateHolder.SaveableStateProvider(Tab.HISTORY) {
            HistoryScreen(
                modifier = modifier,

                // 多选状态上抛给主界面，用于隐藏顶栏返回按钮。
                onSelectionModeChange = onHistorySelectionChange
            )
        }

        Tab.SETTINGS -> tabStateHolder.SaveableStateProvider(Tab.SETTINGS) {
            SettingsScreen(
                themeMode = themeMode,
                themeColor = themeColor,
                onThemeModeChange = onThemeModeChange,
                onThemeColorChange = onThemeColorChange,
                onLanguageChange = onLanguageChange,
                licenseOpen = licenseOpen,
                onLicenseOpen = onLicenseOpen,
                onLicenseClose = onLicenseClose,
                modifier = modifier
            )
        }
    }
}
