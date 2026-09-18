package io.github.pia300.highlightreplay.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.core.net.toUri
import io.github.pia300.highlightreplay.R
import io.github.pia300.highlightreplay.collectMissingRuntimePermissions
import io.github.pia300.highlightreplay.data.KEY_AUDIO_PROMPTED
import io.github.pia300.highlightreplay.data.KEY_BATTERY_PROMPTED
import io.github.pia300.highlightreplay.data.KEY_NOTIFICATION_PROMPTED
import io.github.pia300.highlightreplay.data.KEY_OVERLAY_PROMPTED
import io.github.pia300.highlightreplay.data.RecorderPrefs
import io.github.pia300.highlightreplay.data.defaultPrefs
import io.github.pia300.highlightreplay.data.promptPrefs
import io.github.pia300.highlightreplay.service.FloatingControlService
import io.github.pia300.highlightreplay.service.RecorderService

/**
 * 权限申请、悬浮窗/电池优化引导与屏幕捕获授权流程；构造期创建，
 * 使活动结果回调在 Activity 进入 STARTED 前完成注册。
 *
 * @param activity 宿主 Activity，提供权限申请、系统页跳转与服务启动。
 * @param recorderService 已连接的录制服务；未连接时为 null。
 * @param setContinueAfterPermissions 置「权限结果后继续投影授权」标记。
 * @param shouldContinueProjection 读取「权限结果后继续投影授权」标记。
 * @param onContinueConsumed 消费「权限结果后继续投影授权」标记。
 * @param onNotificationRationale 通知权限被拒时展示说明弹窗。
 * @param onOverlayRationale 悬浮窗权限被拒时展示说明弹窗。
 * @param onScheduleBindAfterStart 录制服务启动后延迟重试绑定。
 */
class RecordingStartFlow(
    private val activity: ComponentActivity,
    private val recorderService: () -> RecorderService?,
    private val setContinueAfterPermissions: (Boolean) -> Unit,
    private val shouldContinueProjection: () -> Boolean,
    private val onContinueConsumed: () -> Unit,
    private val onNotificationRationale: () -> Unit,
    private val onOverlayRationale: () -> Unit,
    private val onScheduleBindAfterStart: () -> Unit
) {

    /** 日志标签；首装引导标记键定义在 data/PromptPrefs.kt。 */
    private companion object {
        private const val TAG = "MainActivity"
    }

    /** 运行时权限回调：点击录制发起的请求无论结果都继续拉起屏幕捕获授权，权限被拒只影响对应能力。 */
    private val permissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
            val prefs = activity.promptPrefs()
            // 记录拒绝结果：冷启动自动检查不再重复请求已拒绝的权限，显式发起（录制按钮）仍会请求。
            permissions[Manifest.permission.RECORD_AUDIO]?.let {
                if (!it) prefs.edit { putBoolean(KEY_AUDIO_PROMPTED, true) }
            }
            // 通知权限被拒时展示说明弹窗（每安装仅首次），其他权限被拒仅提示；仅显式返回 false 才算被拒。
            val notificationResult = permissions[Manifest.permission.POST_NOTIFICATIONS]
            val notificationDenied = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    notificationResult != null && !notificationResult
            if (notificationDenied) {
                if (!prefs.getBoolean(KEY_NOTIFICATION_PROMPTED, false)) {
                    prefs.edit { putBoolean(KEY_NOTIFICATION_PROMPTED, true) }
                    onNotificationRationale()
                }
            } else {
                // 其他权限被拒时仅提示。
                ToastCenter.show(activity, R.string.toast_permission_denied, Toast.LENGTH_SHORT)
            }
        }
        // 悬浮窗授权引导只在首装冷启动路径进行；显式录制路径直接进入屏幕捕获授权，避免两个系统页叠加。
        if (shouldContinueProjection()) {
            onContinueConsumed()
            launchProjectionConsent()
        }
    }

    /** 悬浮窗授权页回调：授权成功且录制中则立即拉起悬浮球；仍未授权则展示说明弹窗。 */
    private val overlayPermissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(activity)) {
            val prefs = activity.defaultPrefs()
            // 自启条件与录制服务 ensureFloatingService 一致：未手动隐藏且允许自动显示时才拉起。
            if (RecorderService.isRunning &&
                !prefs.getBoolean(RecorderPrefs.KEY_FLOATING_HIDDEN, false) &&
                prefs.getBoolean(
                    RecorderPrefs.KEY_AUTO_SHOW_FLOATING,
                    RecorderPrefs.AUTO_SHOW_FLOATING_DEFAULT
                )
            ) {
                try {
                    activity.startService(Intent(activity, FloatingControlService::class.java))
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to start floating service after overlay grant: ${e.message}", e)
                }
            }
        } else {
            onOverlayRationale()
        }
        // 无论悬浮窗授权结果如何，返回后继续推进电池优化豁免申请（串行，避免系统页互相覆盖）。
        requestBatteryExemptionOnce()
    }

    /** 屏幕捕获授权回调：允许后携带结果启动录制前台服务；悬浮球是否出现由录制服务统一决定。 */
    private val projectionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // 把 MediaProjection 授权结果传给录制服务。
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            // 运行时权限已在授权前补齐（见 startRecording），此处不再申请。
            try {
                Intent(activity, RecorderService::class.java).apply {
                    action = RecorderService.ACTION_START
                    putExtra(RecorderService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(RecorderService.EXTRA_RESULT_DATA, result.data)
                    activity.startForegroundService(this)
                }
            } catch (e: Exception) {
                // 前台服务启动失败：记日志、提示用户并刷新磁贴，不再排定绑定重试。
                Log.e(TAG, "Failed to start recorder service: ${e.message}", e)
                ToastCenter.show(activity, R.string.error_recording_start_failed, Toast.LENGTH_SHORT)
                activity.sendBroadcast(Intent(RecorderService.ACTION_TILE_UPDATE).apply {
                    setPackage(activity.packageName)
                })
                return@registerForActivityResult
            }

            // 服务可能仍在启动，延迟 1 秒再尝试绑定。
            onScheduleBindAfterStart()
        } else {

            // 用户拒绝授权：提示并刷新快捷设置磁贴。
            ToastCenter.show(activity, R.string.toast_permission_denied, Toast.LENGTH_SHORT)

            activity.sendBroadcast(Intent(RecorderService.ACTION_TILE_UPDATE).apply {
                setPackage(activity.packageName)
            })
        }
    }

    /** 收集仍缺失的运行时权限（录音/通知/旧版存储），实现见 collectMissingRuntimePermissions。 */
    private fun missingRuntimePermissions(): List<String> =
        collectMissingRuntimePermissions(activity)

    /** 冷启动自动检查：仅请求本安装内尚未被拒过的权限（拒绝历史见偏好键）；无缺失不弹窗。 */
    fun autoCheckPermissions() {
        val prefs = activity.promptPrefs()
        val missing = missingRuntimePermissions().filter { perm ->
            when (perm) {
                Manifest.permission.POST_NOTIFICATIONS ->
                    !prefs.getBoolean(KEY_NOTIFICATION_PROMPTED, false)

                Manifest.permission.RECORD_AUDIO -> !prefs.getBoolean(KEY_AUDIO_PROMPTED, false)
                else -> true
            }
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    /** 打开系统通知设置页，供用户手动开启通知权限。 */
    fun openNotificationSettings() {
        try {
            // 部分厂商系统可能不支持该 Intent；失败提示与用户是否拒绝权限无关。
            activity.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                }
            )
        } catch (e: Exception) {

            ToastCenter.show(activity, R.string.error_open_notification_settings, Toast.LENGTH_SHORT)
        }
    }

    /** 未获得悬浮窗权限时跳转到系统授权页；不支持该 Intent 时降级为说明弹窗。 */
    fun checkOverlayPermission() {
        if (Settings.canDrawOverlays(activity)) return
        try {
            overlayPermissionLauncher.launch(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${activity.packageName}".toUri())
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open overlay permission page: ${e.message}", e)
            onOverlayRationale()
        }
    }

    /** 首装引导编排：悬浮窗授权页只自动引导一次；电池优化豁免串行推进，避免系统页互相覆盖。 */
    fun checkOverlayPermissionAndBattery() {
        if (Settings.canDrawOverlays(activity)) {
            requestBatteryExemptionOnce()
            return
        }
        // 已引导过（无论授予或拒绝）不再自动跳转；仍可从设置页悬浮窗权限行手动授权。
        val prompted = activity.promptPrefs().getBoolean(KEY_OVERLAY_PROMPTED, false)
        if (prompted) {
            requestBatteryExemptionOnce()
            return
        }
        activity.promptPrefs().edit { putBoolean(KEY_OVERLAY_PROMPTED, true) }
        // 授权页返回后（overlayPermissionLauncher 回调）再申请电池豁免。
        checkOverlayPermission()
    }

    /**
     * 首装引导：直接弹出系统的「允许忽略电池优化」确认框（长时间录制防 Doze 中断）。
     *
     * 部分 ROM/策略下该动作不可用，回退到全局电池优化列表页让用户手动放行。
     */
    private fun requestBatteryExemptionOnce() {
        val prefs = activity.promptPrefs()
        if (prefs.getBoolean(KEY_BATTERY_PROMPTED, false)) return
        prefs.edit { putBoolean(KEY_BATTERY_PROMPTED, true) }
        if (isIgnoringBatteryOptimizations()) return
        try {
            activity.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = "package:${activity.packageName}".toUri()
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Battery exemption dialog unavailable, falling back: ${e.message}")
            try {
                activity.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e2: Exception) {
                Log.w(TAG, "Battery optimization settings unavailable: ${e2.message}")
            }
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(activity.packageName)
    }

    /** 录制入口：先补齐缺失的运行时权限，再发起屏幕捕获授权；权限弹窗结束后无论结果都继续授权流程。 */
    fun startRecording() {
        val missing = missingRuntimePermissions()
        if (missing.isEmpty()) {
            launchProjectionConsent()
            return
        }
        // 置继续标记再请求：权限回调据此决定是否接着发起投影授权。
        setContinueAfterPermissions(true)
        permissionLauncher.launch(missing.toTypedArray())
    }

    /** 通过 MediaProjection 发起系统屏幕捕获授权。 */
    fun launchProjectionConsent() {
        val projectionManager =
            activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(createCaptureIntent(projectionManager))
    }

    /**
     * 构造屏幕捕获授权 Intent：转调唯一构造点 [newScreenCaptureIntent]，与磁贴冷启动入口
     * （[io.github.pia300.highlightreplay.RecordingStartActivity]）请求同一捕获范围。
     */
    private fun createCaptureIntent(projectionManager: MediaProjectionManager): Intent =
        projectionManager.newScreenCaptureIntent()

    /** 停止录制；悬浮球与状态复位由录制服务统一处理。 */
    fun stopRecording() {
        val service = recorderService()
        if (service != null) {
            service.stop()
        } else {
            // 绑定失效兜底：与磁贴/通知路径一致，经 startService 下发停止命令。
            try {
                activity.startService(Intent(activity, RecorderService::class.java).apply {
                    action = RecorderService.ACTION_STOP
                })
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start recorder service for stop: ${e.message}", e)
            }
        }
    }
}
