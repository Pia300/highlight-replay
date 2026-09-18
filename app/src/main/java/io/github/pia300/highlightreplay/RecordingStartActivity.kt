package io.github.pia300.highlightreplay

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.github.pia300.highlightreplay.data.LanguagePrefs
import io.github.pia300.highlightreplay.service.RecorderService
import io.github.pia300.highlightreplay.ui.ToastCenter
import io.github.pia300.highlightreplay.ui.newScreenCaptureIntent

/** 录制启动流程入口：请求屏幕捕获授权与运行时权限，通过后启动 RecorderService 前台服务。 */
class RecordingStartActivity : ComponentActivity() {

    /** 日志标签与流程防重入标记。 */
    companion object {
        private const val TAG = "RecordingStartActivity"

        /** 授权流程进行中标记，防止重复拉起授权。 */
        private var flowActive = false

        /** 等待录制就绪的轮询间隔（毫秒）。 */
        private const val POLL_INTERVAL_MS = 100L
        /** 录制就绪等待上限（毫秒），超时直接关闭本页（错误由服务侧提示）。 */
        private const val WAIT_FOR_CAPTURE_TIMEOUT_MS = 10_000L
        /** 启动宽限期（毫秒）：服务经 AMS 异步派发，此期间“尚未运行”不算启动失败。 */
        private const val START_WAIT_GRACE_MS = 1_500L
        /** 重建实例回到前台后仍无会话时的关闭探针（毫秒）。 */
        private const val FAILED_START_FALLBACK_MS = 3_000L
    }

    /** 屏幕捕获授权结果回调：成功则启动服务并等待录制就绪；失败/取消则刷新磁贴并结束本页。 */
    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {

            // 授权成功：以前台服务方式启动录制服务。
            try {
                startForegroundService(
                    Intent(this, RecorderService::class.java).apply {
                        action = RecorderService.ACTION_START
                        putExtra(RecorderService.EXTRA_RESULT_CODE, result.resultCode)
                        putExtra(RecorderService.EXTRA_RESULT_DATA, result.data)
                    }
                )
                // 前台服务启动可能失败（如系统限制）：提示用户并刷新磁贴。
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start recorder service: ${e.message}")
                ToastCenter.show(
                    this,
                    LanguagePrefs.string(this, R.string.error_recording_start_failed)
                )

                // 通知快捷设置磁贴刷新（仅限本应用包）。
                sendBroadcast(Intent(RecorderService.ACTION_TILE_UPDATE).apply {
                    setPackage(packageName)
                })
                finish()
                return@registerForActivityResult
            }
            // 保持本页前台，直到录制管线就绪再关闭。
            waitForCaptureAndFinish()
        } else {
            // 用户拒绝授权：刷新磁贴后结束本页。
            sendBroadcast(Intent(RecorderService.ACTION_TILE_UPDATE).apply {
                setPackage(packageName)
            })
            finish()
        }
    }

    /** 运行时权限回调：弹窗结束即继续屏幕捕获授权；录音/通知被拒只影响对应能力，不阻断录制。 */
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {

        // 权限请求结束（授予或拒绝）后继续发起屏幕捕获授权。
        launchProjectionConsent()
    }

    /** Activity 入口：按录制状态与流程标记决定是否发起授权，避免重复启动流程。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 录制服务已在运行，直接结束本页。
        if (RecorderService.isRunning) {
            finish()
            return
        }

        // 配置变更重建时保留流程标记；挂起的授权结果随 ActivityResultRegistry 恢复到新实例。
        if (savedInstanceState != null && flowActive) {
            // 本实例接管在途流程：仅其真正结束时（isFinishing）清除全局标记。
            ownsFlow = true
            if (RecorderService.isRunning) {
                waitForCaptureAndFinish()
            } else {
                // 被授权页遮挡期间不能提前关闭本页，否则授权结果会投递给已销毁实例而丢失。
                waitForPendingConsentOrFinish()
            }
            return
        }

        // 冷启动（无保存状态）而标记仍在：旧任务被移除时在途实例随之消亡，静态标记却会残留，
        // 复位后重新发起流程，避免授权入口被陈旧标记永久封死。
        if (flowActive) {
            flowActive = false
        }
        beginRecordingFlow()
    }

    /** 单实例模式收到新启动意图：无进行中流程时重新发起授权。 */
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        if (!flowActive) {
            beginRecordingFlow()
        }
    }

    /** 仅在 Activity 真正结束时清除流程标记；配置变更销毁不清除。 */
    override fun onDestroy() {
        super.onDestroy()

        pollHandler.removeCallbacksAndMessages(null)
        lifecycle.removeObserver(flowProbeObserver)
        fallbackProbe = null
        // 仅流程所有者（isFinishing）清除全局标记，避免误清其他实例仍在进行的流程。
        if (isFinishing && ownsFlow) {
            flowActive = false
        }
    }

    /**
     * 置流程标记并经 MediaProjectionManager 拉起系统屏幕捕获授权对话框。
     *
     * 授权 Intent 经唯一构造点 [newScreenCaptureIntent] 取得，与应用内「开始录制」入口
     * 请求同一捕获范围；构造点唯一性由构建任务 `checkCaptureIntentConstruction` 守。
     */
    private fun launchProjectionConsent() {
        flowActive = true
        try {
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projectionLauncher.launch(manager.newScreenCaptureIntent())
        } catch (e: Exception) {
            // 授权发起失败：复位标记并提示用户。
            Log.e(TAG, "Failed to launch projection consent: ${e.message}")
            flowActive = false
            ToastCenter.show(
                this,
                LanguagePrefs.string(this, R.string.error_recording_start_failed)
            )
            // 通知快捷设置磁贴刷新（仅限本应用包）。
            sendBroadcast(Intent(RecorderService.ACTION_TILE_UPDATE).apply {
                setPackage(packageName)
            })
            finish()
        }
    }

    /** 流程入口：置防重入标记后先请求缺失的运行时权限，弹窗结束后经 permissionLauncher 继续屏幕捕获授权。 */
    private fun beginRecordingFlow() {
        flowActive = true
        ownsFlow = true
        requestRuntimePermissionsThenConsent()
    }

    /** 收集缺失的运行时权限并一次性请求；无缺失时直接发起屏幕捕获授权。 */
    private fun requestRuntimePermissionsThenConsent() {
        val permissions = collectMissingRuntimePermissions(this)
        // 无缺失权限时直接发起授权；有缺失则弹窗并由回调继续。
        if (permissions.isEmpty()) {
            launchProjectionConsent()
        } else {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    /** 本实例是否为当前在途授权流程的所有者；只有所有者才能清除全局防重入标记。 */
    private var ownsFlow = false

    /** 重建后等待挂起授权结果的兜底探针；非空表示正在等待。 */
    private var fallbackProbe: Runnable? = null

    /** 生命周期观察者：页面在前台时运行兜底探针，退到后台立即停表。 */
    private val flowProbeObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_RESUME -> {
                // 回到前台：先移除旧调度再重启探针。
                fallbackProbe?.let {
                    pollHandler.removeCallbacks(it)
                    pollHandler.post(it)
                }
            }

            Lifecycle.Event.ON_PAUSE -> {
                // 离开前台：停止轮询。
                fallbackProbe?.let { pollHandler.removeCallbacks(it) }
            }

            else -> {}
        }
    }

    /** 就绪等待轮询专用主线程 Handler（不用 decorView：旋转重建后旧 view 销毁会断链）。 */
    private val pollHandler = Handler(Looper.getMainLooper())

    /** 保持本页前台直至录制就绪（Android 15 的 startForeground 需前台 Activity），就绪/失败/超时/销毁即关闭本页。 */
    private fun waitForCaptureAndFinish() {
        val start = SystemClock.uptimeMillis()
        val poll = object : Runnable {
            override fun run() {
                val elapsed = SystemClock.uptimeMillis() - start
                val ready = RecorderService.captureReady
                val sessionAlive = RecorderService.isRunning
                val expired = elapsed > WAIT_FOR_CAPTURE_TIMEOUT_MS
                if (ready || (elapsed > START_WAIT_GRACE_MS && !sessionAlive) ||
                    expired || isFinishing || isDestroyed
                ) {
                    finish()
                    return
                }
                pollHandler.postDelayed(this, POLL_INTERVAL_MS)
            }
        }
        pollHandler.post(poll)
    }

    /** 重建后等待挂起授权结果的兜底轮询：回到前台仍无会话且超时则关闭本页；仅在页面处在前台时轮询。 */
    private fun waitForPendingConsentOrFinish() {
        if (fallbackProbe != null) return // 幂等：已在等待中
        val start = SystemClock.uptimeMillis()
        val probe = object : Runnable {
            override fun run() {
                if (RecorderService.isRunning || isFinishing || isDestroyed) {
                    finish()
                    return
                }
                val resumed =
                    lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                if (resumed) {
                    if (SystemClock.uptimeMillis() - start > FAILED_START_FALLBACK_MS) {
                        finish()
                        return
                    }
                    // 前台等待授权结果：以短间隔继续轮询。
                    pollHandler.postDelayed(this, POLL_INTERVAL_MS)
                }
                // 未在前台时不重复投递，由 flowProbeObserver 在页面重新 RESUMED 时重启。
            }
        }
        fallbackProbe = probe
        lifecycle.addObserver(flowProbeObserver)
        pollHandler.post(probe)
    }
}
