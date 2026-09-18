package io.github.pia300.highlightreplay.ui

import android.widget.Toast
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.pia300.highlightreplay.R
import io.github.pia300.highlightreplay.ui.components.isLandscapeLayout
import io.github.pia300.highlightreplay.ui.screens.control.ControlUiState

/** 底部/侧边导航的页签枚举：每页签含标题资源与图标。 */
enum class Tab(
    @param:androidx.annotation.StringRes val labelRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    /** 控制页（录制/保存）。 */
    CONTROL(R.string.tab_control, Icons.Filled.RadioButtonChecked),
    /** 历史记录页。 */
    HISTORY(R.string.tab_history, Icons.Filled.History),
    /** 设置页。 */
    SETTINGS(R.string.tab_settings, Icons.Filled.Settings)
}

/**
 * 主界面骨架：顶栏/底栏、错误提示、权限说明弹窗与导航分发。
 *
 * @param currentTab 当前页签。
 * @param onTabSelected 页签切换回调（同时关闭许可证页）。
 * @param licenseOpen 许可证页是否展开。
 * @param onLicenseOpen 打开许可证页。
 * @param onLicenseClose 关闭许可证页。
 * @param historySelectionActive 历史页是否处于多选模式（用于隐藏顶栏）。
 * @param onHistorySelectionChange 历史页多选模式变化回调。
 * @param settingsChanged 录制中是否修改过设置。
 * @param uiState 控制页状态读取器：在组合作用域内取值，避免每秒一次的状态更新重组整个骨架。
 * @param onErrorMessageConsumed 错误信息已提示，清空状态。
 * @param onStartRecording 开始录制。
 * @param onStopRecording 停止录制。
 * @param onSaveReplay 保存回放。
 * @param showNotificationRationale 是否显示通知权限说明弹窗。
 * @param onNotificationRationaleDismiss 关闭通知权限说明弹窗。
 * @param onNotificationRationaleConfirm 确认通知权限说明弹窗（跳转系统通知设置）。
 * @param showOverlayRationale 是否显示悬浮窗权限说明弹窗。
 * @param onOverlayRationaleDismiss 关闭悬浮窗权限说明弹窗。
 * @param onOverlayRationaleConfirm 确认悬浮窗权限说明弹窗（重新跳转授权页）。
 * @param themeMode 主题模式设置值。
 * @param themeColor 配色设置值。
 * @param onThemeModeChange 主题模式变更回调。
 * @param onThemeColorChange 配色变更回调。
 * @param onLanguageChange 语言切换回调。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    currentTab: Tab,
    onTabSelected: (Tab) -> Unit,
    licenseOpen: Boolean,
    onLicenseOpen: () -> Unit,
    onLicenseClose: () -> Unit,
    historySelectionActive: Boolean,
    onHistorySelectionChange: (Boolean) -> Unit,
    settingsChanged: Boolean,
    uiState: () -> ControlUiState,
    onErrorMessageConsumed: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onSaveReplay: () -> Unit,
    showNotificationRationale: Boolean,
    onNotificationRationaleDismiss: () -> Unit,
    onNotificationRationaleConfirm: () -> Unit,
    showOverlayRationale: Boolean,
    onOverlayRationaleDismiss: () -> Unit,
    onOverlayRationaleConfirm: () -> Unit,
    themeMode: String,
    themeColor: String,
    onThemeModeChange: (String) -> Unit,
    onThemeColorChange: (String) -> Unit,
    onLanguageChange: () -> Unit
) {
    val context = LocalContext.current

    // 错误消息弹出 Toast 后立即清空，避免重复提示。
    // 用 derivedStateOf 只订阅 errorMessage：否则录制中每秒一次的 uiState 更新（elapsedSeconds）
    // 会让 MainScreen 整个作用域（Scaffold、顶栏/底栏与当前页签）每秒重复执行一次。
    val latestUiState by rememberUpdatedState(uiState)
    val errorMessage by remember { derivedStateOf { latestUiState().errorMessage } }
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            ToastCenter.show(context, it, Toast.LENGTH_LONG)
            onErrorMessageConsumed()
        }
    }

    Scaffold(

        topBar = {
            // 仅竖屏且非控制页时显示顶栏；横屏由导航栏承担。
            if (!isLandscapeLayout() && currentTab != Tab.CONTROL) {
                when (currentTab) {
                    Tab.SETTINGS -> if (licenseOpen) {
                        LicenseTopBar(onBack = onLicenseClose)
                    } else {
                        TabTopBar(currentTab)
                    }

                    Tab.HISTORY -> if (!historySelectionActive) {
                        TabTopBar(currentTab)
                    }

                    // CONTROL 已被外层条件排除；Kotlin 1.7+ 要求枚举 when 穷尽，故显式列出。
                    Tab.CONTROL -> Unit
                }
            }
        },
        bottomBar = {

            // 仅竖屏显示底部导航栏。
            if (!isLandscapeLayout()) {
                PortraitNavigationBar(
                    currentTab = currentTab,
                    onTabSelected = onTabSelected
                )
            }
        }
    ) { padding ->
        MainNavigation(
            isLandscape = isLandscapeLayout(),
            currentTab = currentTab,
            onTabSelected = onTabSelected,
            modifier = Modifier.padding(padding)
        ) { contentModifier ->
            TabContent(
                currentTab = currentTab,
                onTabSelected = onTabSelected,
                licenseOpen = licenseOpen,
                onLicenseOpen = onLicenseOpen,
                onLicenseClose = onLicenseClose,
                onHistorySelectionChange = onHistorySelectionChange,
                settingsChanged = settingsChanged,
                uiState = uiState,
                onStartRecording = onStartRecording,
                onStopRecording = onStopRecording,
                onSaveReplay = onSaveReplay,
                themeMode = themeMode,
                themeColor = themeColor,
                onThemeModeChange = onThemeModeChange,
                onThemeColorChange = onThemeColorChange,
                onLanguageChange = onLanguageChange,
                modifier = contentModifier
            )
        }
    }

    ShellRationaleDialogs(
        showNotificationRationale = showNotificationRationale,
        onNotificationRationaleDismiss = onNotificationRationaleDismiss,
        onNotificationRationaleConfirm = onNotificationRationaleConfirm,
        showOverlayRationale = showOverlayRationale,
        onOverlayRationaleDismiss = onOverlayRationaleDismiss,
        onOverlayRationaleConfirm = onOverlayRationaleConfirm
    )
}

/** 许可证页顶栏：居中标题加返回按钮。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun LicenseTopBar(onBack: () -> Unit) {
    CenterAlignedTopAppBar(
        title = { Text(stringResource(R.string.settings_license)) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.settings_license_back)
                )
            }
        },

        windowInsets = WindowInsets(0, 0, 0, 0),
        modifier = Modifier.compactTopBarInsets()
    )
}

/** 普通页签顶栏：居中显示当前页签标题。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabTopBar(currentTab: Tab) {
    CenterAlignedTopAppBar(
        title = { Text(stringResource(currentTab.labelRes)) },

        windowInsets = WindowInsets(0, 0, 0, 0),
        modifier = Modifier.compactTopBarInsets()
    )
}

/** 让顶栏避开状态栏并固定为 48dp 高度的修饰符扩展。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Modifier.compactTopBarInsets(): Modifier {

    // 仅保留顶部与水平安全区，并标记已消费以防重复内边距。
    val insets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    return windowInsetsPadding(insets)
        .consumeWindowInsets(insets)
        .height(48.dp)
}
