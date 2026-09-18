package io.github.pia300.highlightreplay.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import io.github.pia300.highlightreplay.service.RecorderService
import io.github.pia300.highlightreplay.service.RecorderState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 管理服务绑定生命周期；连接后同步状态并收集状态流（单一状态源）。
 *
 * @param activity 宿主 Activity，提供绑定/解绑、生命周期作用域与 decorView 调度。
 * @param onRecorderState 连接后同步与状态流推送：回传录制状态快照。
 * @param onDisconnectedReset 系统断开连接时：复位界面状态。
 * @param onStoppedReset 服务已停止时：复位界面状态。
 */
class RecorderServiceBinding(
    private val activity: ComponentActivity,
    private val onRecorderState: (RecorderState) -> Unit,
    private val onDisconnectedReset: () -> Unit,
    private val onStoppedReset: () -> Unit
) {

    /** 日志标签与绑定重试常量。 */
    companion object {
        private const val TAG = "MainActivity"

        private const val MAX_BIND_ATTEMPTS = 10
        private const val BIND_RETRY_DELAY_MS = 300L
        private const val BIND_RETRY_BACKOFF_MS = 2000L
        private const val BIND_RETRY_BACKOFF_MAX_MS = 64000L
        private const val BIND_START_DELAY_MS = 1000L
    }

    /** 已连接的录制服务；未连接时为 null。 */
    var recorderService: RecorderService? = null
        private set

    private var isBound = false

    /** Activity 已停止：此后到达的连接回调须立即解绑，避免绑定滞留在已停止的界面（服务因此无法销毁）。 */
    private var stopped = false

    private var stateCollectJob: Job? = null

    private var bindAttempts = 0

    /** 达到重试上限后的退避轮次，用于翻倍延长重试间隔。 */
    private var backoffRounds = 0

    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            // 回调可能晚于界面停止到达：此时立即解绑，不建立订阅。
            if (stopped) {
                try {
                    activity.unbindService(this)
                } catch (_: Exception) {
                    // 绑定已失效时解绑会抛异常，忽略即可。
                }
                return
            }

            val binder = service as? RecorderService.LocalBinder ?: return
            recorderService = binder.getService()
            isBound = true
            // 连接成功即重置重试计数。
            bindAttempts = 0

            // 连接后立即同步一次录制状态。
            onRecorderState(RecorderService.currentState())

            // 先取消旧的收集任务，避免重复订阅。
            stateCollectJob?.cancel()
            stateCollectJob = activity.lifecycleScope.launch {
                RecorderService.stateFlow.collect { s ->
                    onRecorderState(s)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            // 系统断开连接（防御性处理）：先取消状态收集并清空引用，避免残留回调写回过期状态。
            stateCollectJob?.cancel()
            stateCollectJob = null
            onDisconnectedReset()
            recorderService = null
            if (isBound) {
                isBound = false
                // 解除已失效的绑定，避免后续 bindService 被拒或产生重复回调。
                try {
                    activity.unbindService(serviceConnection)
                } catch (_: Exception) {
                    // 绑定已失效时再次解绑会抛异常，忽略即可。
                }
            }
            // 会话仍在运行时重新发起绑定（受 MAX_BIND_ATTEMPTS 上限约束）。
            if (RecorderService.isRunning) {
                bindAttempts = 0
                tryBindService()
            }
        }
    }

    /** 延迟绑定用回调，经 decorView 的 postDelayed 调度。 */
    private val tryBindRunnable = Runnable { tryBindService() }

    /** 取消待执行的绑定回调，重置计数后延迟调度绑定（兼容服务仍在启动的竞态）。 */
    fun scheduleBind() {
        stopped = false
        activity.window.decorView.removeCallbacks(tryBindRunnable)
        bindAttempts = 0
        activity.window.decorView.postDelayed(tryBindRunnable, BIND_RETRY_DELAY_MS)
    }

    /** 录制服务刚启动后延迟调度绑定（服务可能仍在启动）。 */
    fun scheduleBindAfterServiceStart() {
        activity.window.decorView.removeCallbacks(tryBindRunnable)
        activity.window.decorView.postDelayed(tryBindRunnable, BIND_START_DELAY_MS)
    }

    /** 移除待执行的绑定重试回调。 */
    fun cancelPendingBind() {
        activity.window.decorView.removeCallbacks(tryBindRunnable)
    }

    /** 内部解绑：先取消状态收集再解绑服务，避免泄漏与重复回调。 */
    fun unbind() {
        // 先置停止标记：在途的 bindService 受理后其回调据此立即解绑。
        stopped = true
        stateCollectJob?.cancel()
        stateCollectJob = null
        if (isBound) {
            try {

                // 服务端不持有 Activity 引用，解绑仅终止框架对该连接的回调与资源占用。
                activity.unbindService(serviceConnection)
            } catch (e: Exception) {

                // 已解绑时再次解绑会抛异常，忽略即可。
                Log.e(TAG, "Failed to unbind: ${e.message}", e)
            }
            isBound = false
        }
    }

    /** 尝试绑定：仅服务运行中且未绑定时发起；失败按 [MAX_BIND_ATTEMPTS] 上限重试。 */
    private fun tryBindService() {
        if (RecorderService.isRunning) {
            if (!isBound) {
                // bindService 返回 false 表示未被受理（如服务仍在启动），按失败进入重试分支。
                val accepted = try {
                    activity.bindService(
                        Intent(activity, RecorderService::class.java),
                        serviceConnection,
                        Context.BIND_AUTO_CREATE
                    )
                } catch (e: Exception) {
                    // 绑定抛异常（如服务未注册）时记日志并进入重试分支。
                    Log.e(TAG, "Failed to bind service: ${e.message}", e)
                    false
                }
                if (accepted) {
                    // 已受理：onServiceConnected 会置 isBound 并重置计数。
                    return
                }
                Log.w(TAG, "bindService not accepted; retrying later")
            } else {
                // 已绑定：不再排定重试。
                return
            }
        } else {
            // 服务已停止：复位界面状态，不排定重试。
            onStoppedReset()
            recorderService = null
            return
        }

        // 未达上限继续重试；达上限而服务仍在运行则降频继续，避免会话期间界面停留在过期状态。
        if (bindAttempts < MAX_BIND_ATTEMPTS) {
            bindAttempts++
            activity.window.decorView.postDelayed(tryBindRunnable, BIND_RETRY_DELAY_MS)
        } else if (RecorderService.isRunning) {
            // 已达上限但服务仍在运行：持续重试，退避间隔按轮次翻倍并封顶，避免长期占用主线程。
            bindAttempts = 0
            backoffRounds++
            val delay = (BIND_RETRY_BACKOFF_MS shl (backoffRounds - 1).coerceAtMost(5))
                .coerceAtMost(BIND_RETRY_BACKOFF_MAX_MS)
            activity.window.decorView.postDelayed(tryBindRunnable, delay)
        } else {
            bindAttempts = 0
            backoffRounds = 0
        }
    }
}
