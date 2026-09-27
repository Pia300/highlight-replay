package io.github.pia300.highlightreplay

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.pia300.highlightreplay.data.LanguagePrefs
import io.github.pia300.highlightreplay.data.ThemePrefs
import io.github.pia300.highlightreplay.service.RecorderService
import io.github.pia300.highlightreplay.ui.MainScreen
import io.github.pia300.highlightreplay.ui.RecordingStartFlow
import io.github.pia300.highlightreplay.ui.Tab
import io.github.pia300.highlightreplay.ui.screens.control.ControlViewModel
import io.github.pia300.highlightreplay.ui.theme.HighlightReplayTheme

/** 主活动：承载控制/历史/设置三个页面，负责权限申请与录制启动流程。 */
class MainActivity : ComponentActivity() {

    /** 日志标签与状态保存键。 */
    companion object {
        private const val TAG = "MainActivity"
        private const val KEY_TAB = "tab"
        private const val KEY_LICENSE_OPEN = "license_open"
        private const val KEY_CONTINUE_AFTER_PERMISSIONS = "continue_after_permissions"
        private const val KEY_HISTORY_SELECTION = "history_selection"
        private const val KEY_NOTIFICATION_RATIONALE = "notification_rationale"
        private const val KEY_OVERLAY_RATIONALE = "overlay_rationale"
    }

    private var currentTab by mutableStateOf(Tab.CONTROL)
    private var licenseOpen by mutableStateOf(false)
    private var historySelectionActive by mutableStateOf(false)

    private var showNotificationRationale by mutableStateOf(false)

    /** 悬浮窗权限被拒后的说明弹窗开关（从系统授权页返回仍未授权时置位）。 */
    private var showOverlayRationale by mutableStateOf(false)

    /** 点击录制后的权限继续标记：仅其为真时权限回调才发起投影授权（与 onCreate 自动检查区分）。 */
    private var continueAfterPermissions = false

    private var themeMode by mutableStateOf(ThemePrefs.MODE_SYSTEM)
    private var themeColor by mutableStateOf(ThemePrefs.COLOR_DYNAMIC)

    /** 权限申请、悬浮窗/电池优化引导与屏幕捕获授权流程；构造期创建以完成活动结果回调注册。 */
    private val startFlow = RecordingStartFlow(
        activity = this,
        setContinueAfterPermissions = { continueAfterPermissions = it },
        shouldContinueProjection = { continueAfterPermissions },
        onContinueConsumed = { continueAfterPermissions = false },
        onNotificationRationale = { showNotificationRationale = true },
        onOverlayRationale = { showOverlayRationale = true }
    )

    /** 在上下文创建前应用用户语言与主题模式，使界面与系统栏对比层从开始即符合目标主题。 */
    override fun attachBaseContext(newBase: Context) {

        val language = LanguagePrefs.current(newBase)
        LanguagePrefs.syncDefaultLocale(language)
        val localized = LanguagePrefs.wrap(newBase, language)
        // 主题模式与系统配置不一致时（如系统浅色 + 应用深色）包裹夜间配置，
        // 使平台窗口背景/系统栏对比度与 Compose 主题保持一致（否则手势条区域出现浅色留白）。
        super.attachBaseContext(applyThemeMode(localized))
    }

    /** 按主题设置包裹 Context 的夜间模式；跟随系统时原样返回。 */
    private fun applyThemeMode(base: Context): Context {
        val mode = ThemePrefs.themeMode(base)
        if (mode == ThemePrefs.MODE_SYSTEM) return base
        val night = if (mode == ThemePrefs.MODE_DARK) {
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        } else {
            android.content.res.Configuration.UI_MODE_NIGHT_NO
        }
        val config = android.content.res.Configuration(base.resources.configuration)
        val mask = android.content.res.Configuration.UI_MODE_NIGHT_MASK
        config.uiMode = (config.uiMode and mask.inv()) or night
        return base.createConfigurationContext(config)
    }

    /** 初始化主题、恢复保存状态并搭建 Compose 界面。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 从持久化设置加载主题模式与配色。
        themeMode = ThemePrefs.themeMode(this)
        themeColor = ThemePrefs.themeColor(this)

        // 有保存状态时恢复页签与弹层。
        if (savedInstanceState != null) {

            val tabOrdinal = savedInstanceState.getInt(KEY_TAB, 0)
                .coerceIn(0, Tab.entries.lastIndex)
            currentTab = Tab.entries[tabOrdinal]

            licenseOpen = savedInstanceState.getBoolean(KEY_LICENSE_OPEN, false)

            // 恢复权限结果后继续投影授权的标记，避免重建后流程中断或重复拉起。
            continueAfterPermissions =
                savedInstanceState.getBoolean(KEY_CONTINUE_AFTER_PERMISSIONS, false)

            historySelectionActive =
                savedInstanceState.getBoolean(KEY_HISTORY_SELECTION, false)

            // 说明弹窗状态同样需要恢复，否则旋转后提示直接消失、用户再也看不到引导。
            showNotificationRationale =
                savedInstanceState.getBoolean(KEY_NOTIFICATION_RATIONALE, false)
            showOverlayRationale = savedInstanceState.getBoolean(KEY_OVERLAY_RATIONALE, false)
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }

        setContent {

            val controlViewModel: ControlViewModel = viewModel()
            val controlUiState by controlViewModel.uiState.collectAsStateWithLifecycle()

            // 由主题模式解析最终是否使用深色主题。
            val darkTheme = ThemePrefs.resolveDark(themeMode, isSystemInDarkTheme())

            HighlightReplayTheme(darkTheme = darkTheme, colorMode = themeColor) {

                val colorScheme = MaterialTheme.colorScheme
                SideEffect {
                    window.decorView.setBackgroundColor(colorScheme.background.toArgb())
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !darkTheme
                        isAppearanceLightNavigationBars = !darkTheme
                    }
                }
                MainScreen(
                    currentTab = currentTab,
                    onTabSelected = { tab ->
                        currentTab = tab
                        licenseOpen = false
                    },
                    licenseOpen = licenseOpen,
                    onLicenseOpen = { licenseOpen = true },
                    onLicenseClose = { licenseOpen = false },
                    historySelectionActive = historySelectionActive,
                    onHistorySelectionChange = { historySelectionActive = it },
                    uiState = { controlUiState },
                    onStartRecording = { startFlow.startRecording() },
                    onStopRecording = { controlViewModel.stopRecording() },
                    onSaveReplay = { controlViewModel.saveReplay() },
                    showNotificationRationale = showNotificationRationale,
                    onNotificationRationaleDismiss = { showNotificationRationale = false },
                    onNotificationRationaleConfirm = {
                        showNotificationRationale = false
                        startFlow.openNotificationSettings()
                    },
                    showOverlayRationale = showOverlayRationale,
                    onOverlayRationaleDismiss = { showOverlayRationale = false },
                    onOverlayRationaleConfirm = {
                        showOverlayRationale = false
                        startFlow.checkOverlayPermission()
                    },
                    themeMode = themeMode,
                    themeColor = themeColor,
                    onThemeModeChange = { mode ->
                        if (mode != themeMode) {
                            ThemePrefs.setThemeMode(this@MainActivity, mode)
                            // 平台主题（窗口背景/系统栏对比层）经 attachBaseContext 应用，需重建才生效。
                            recreate()
                        }
                    },
                    onThemeColorChange = { color ->
                        ThemePrefs.setThemeColor(this@MainActivity, color)
                        themeColor = color
                    },
                    onLanguageChange = {
                        // 切换语言后让录制服务按新语言重建通知文案，再重建界面立即生效。
                        try {
                            startService(Intent(this@MainActivity, RecorderService::class.java).apply {
                                action = RecorderService.ACTION_REFRESH_NOTIFICATION
                            })
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to start notification-refresh service: ${e.message}")
                        }
                        recreate()
                    }
                )
            }
        }

        // 冷启动自动检查；已拒绝过的权限记录在偏好中，不再重复弹系统请求（录制按钮与设置页仍可显式触发）。
        if (savedInstanceState == null) {
            startFlow.autoCheckPermissions()
            // 首装引导：悬浮窗授权页仅自动引导一次（拒绝后不打扰）；电池豁免随后串行申请。
            startFlow.checkOverlayPermissionAndBattery()
        }
    }

    /** 保存页签、弹层状态与权限后继续标记，供重建时恢复。 */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_TAB, currentTab.ordinal)
        outState.putBoolean(KEY_LICENSE_OPEN, licenseOpen)
        outState.putBoolean(KEY_CONTINUE_AFTER_PERMISSIONS, continueAfterPermissions)
        outState.putBoolean(KEY_HISTORY_SELECTION, historySelectionActive)
        outState.putBoolean(KEY_NOTIFICATION_RATIONALE, showNotificationRationale)
        outState.putBoolean(KEY_OVERLAY_RATIONALE, showOverlayRationale)
    }
}
