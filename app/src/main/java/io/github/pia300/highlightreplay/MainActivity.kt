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
import io.github.pia300.highlightreplay.data.LanguagePrefs
import io.github.pia300.highlightreplay.data.ThemePrefs
import io.github.pia300.highlightreplay.service.RecorderService
import io.github.pia300.highlightreplay.ui.MainScreen
import io.github.pia300.highlightreplay.ui.RecorderServiceBinding
import io.github.pia300.highlightreplay.ui.RecordingStartFlow
import io.github.pia300.highlightreplay.ui.Tab
import io.github.pia300.highlightreplay.ui.screens.control.ControlUiState
import io.github.pia300.highlightreplay.ui.theme.HighlightReplayTheme

/** 主活动：承载控制/历史/设置三个页面，负责权限申请与服务绑定。 */
class MainActivity : ComponentActivity() {

    /** 日志标签与状态保存键。 */
    companion object {
        private const val TAG = "MainActivity"
        private const val KEY_TAB = "tab"
        private const val KEY_LICENSE_OPEN = "license_open"
        private const val KEY_SETTINGS_CHANGED = "settings_changed"
        private const val KEY_CONTINUE_AFTER_PERMISSIONS = "continue_after_permissions"
        private const val KEY_HISTORY_SELECTION = "history_selection"
        private const val KEY_NOTIFICATION_RATIONALE = "notification_rationale"
        private const val KEY_OVERLAY_RATIONALE = "overlay_rationale"
    }

    // 初始状态直接取进程级会话状态：旋转重建时不必等服务绑定（约 300ms 后）才纠正，
    // 否则录制中旋转会短暂显示“就绪 / 00:00”。
    private var uiState by mutableStateOf(
        RecorderService.currentState().let { s ->
            ControlUiState(
                isRecording = s.isRunning,
                isSaving = s.isSaving,
                elapsedSeconds = s.elapsedSeconds
            )
        }
    )

    private var currentTab by mutableStateOf(Tab.CONTROL)
    private var licenseOpen by mutableStateOf(false)
    private var historySelectionActive by mutableStateOf(false)

    /** 录制中是否修改过设置（控制页横幅提示“下次生效”），随服务状态流同步。 */
    private var settingsChanged by mutableStateOf(false)
    private var showNotificationRationale by mutableStateOf(false)

    /** 悬浮窗权限被拒后的说明弹窗开关（从系统授权页返回仍未授权时置位）。 */
    private var showOverlayRationale by mutableStateOf(false)

    /** 点击录制后的权限继续标记：仅其为真时权限回调才发起投影授权（与 onCreate 自动检查区分）。 */
    private var continueAfterPermissions = false

    private var themeMode by mutableStateOf(ThemePrefs.MODE_SYSTEM)
    private var themeColor by mutableStateOf(ThemePrefs.COLOR_DYNAMIC)

    /** 管理服务绑定生命周期；连接后同步状态并收集状态流（单一状态源）。 */
    private val serviceBinding = RecorderServiceBinding(
        activity = this,
        onRecorderState = { s ->
            uiState = uiState.copy(
                isRecording = s.isRunning,
                isSaving = s.isSaving,
                elapsedSeconds = s.elapsedSeconds
            )
            settingsChanged = s.settingsStale
        },
        onDisconnectedReset = {
            // 录制/保存态先复位为就绪，避免界面停留在过期状态。
            if (uiState.isRecording || uiState.isSaving) {
                uiState = ControlUiState()
            }
        },
        onStoppedReset = { uiState = ControlUiState() }
    )

    /** 权限申请、悬浮窗/电池优化引导与屏幕捕获授权流程；构造期创建以完成活动结果回调注册。 */
    private val startFlow = RecordingStartFlow(
        activity = this,
        recorderService = { serviceBinding.recorderService },
        setContinueAfterPermissions = { continueAfterPermissions = it },
        shouldContinueProjection = { continueAfterPermissions },
        onContinueConsumed = { continueAfterPermissions = false },
        onNotificationRationale = { showNotificationRationale = true },
        onOverlayRationale = { showOverlayRationale = true },
        onScheduleBindAfterStart = { serviceBinding.scheduleBindAfterServiceStart() }
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

            settingsChanged = savedInstanceState.getBoolean(KEY_SETTINGS_CHANGED, false)

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
                    settingsChanged = settingsChanged,
                    uiState = { uiState },
                    onErrorMessageConsumed = { uiState = uiState.copy(errorMessage = null) },
                    onStartRecording = { startFlow.startRecording() },
                    onStopRecording = { startFlow.stopRecording() },
                    onSaveReplay = {
                        // 保存回放需已连接的服务，未连接时给出错误提示。
                        val service = serviceBinding.recorderService
                        if (service != null) {
                            service.saveReplay()
                        } else {
                            uiState = uiState.copy(
                                errorMessage = getString(R.string.service_not_connected)
                            )
                        }
                    },
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

    /** 进入前台：延迟 300ms 尝试绑定录制服务。 */
    override fun onStart() {
        super.onStart()

        // 重置计数后重新调度绑定，兼容服务仍在启动的竞态。
        serviceBinding.scheduleBind()
    }

    /** 退到后台：取消待绑定回调并解绑服务以释放资源。 */
    override fun onStop() {
        super.onStop()
        serviceBinding.cancelPendingBind()
        serviceBinding.unbind()
    }

    /** 清理：移除绑定重试回调并解绑服务。 */
    override fun onDestroy() {
        super.onDestroy()
        // 移除待执行的绑定重试回调，避免销毁后仍触发。
        serviceBinding.cancelPendingBind()
        serviceBinding.unbind()
    }

    /** 保存页签、弹层状态与权限后继续标记，供重建时恢复。 */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_TAB, currentTab.ordinal)
        outState.putBoolean(KEY_LICENSE_OPEN, licenseOpen)
        outState.putBoolean(KEY_SETTINGS_CHANGED, settingsChanged)
        outState.putBoolean(KEY_CONTINUE_AFTER_PERMISSIONS, continueAfterPermissions)
        outState.putBoolean(KEY_HISTORY_SELECTION, historySelectionActive)
        outState.putBoolean(KEY_NOTIFICATION_RATIONALE, showNotificationRationale)
        outState.putBoolean(KEY_OVERLAY_RATIONALE, showOverlayRationale)
    }
}
