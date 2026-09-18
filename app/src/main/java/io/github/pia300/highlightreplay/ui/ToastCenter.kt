package io.github.pia300.highlightreplay.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import io.github.pia300.highlightreplay.data.LanguagePrefs

/** 全局 Toast 中心：统一在主线程显示，支持全局开关。 */
object ToastCenter {

    private const val TAG = "ToastCenter"

    /** 主线程 Handler；Toast 必须在主线程上显示。 */
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    /** 全局 Toast 开关（@Volatile 保证跨线程可见）。 */
    @Volatile
    private var globalEnabled = true

    /** 设置全局 Toast 开关。 */
    fun setGlobalEnabled(enabled: Boolean) {
        globalEnabled = enabled
    }

    /**
     * 以文本内容显示一条 Toast。
     *
     * [text] 声明为非空，但平台异常的 `message` 是平台类型：它可以是 null，
     * 而 Kotlin 只在实参传入的那一刻才检查，调用点的 `?:` 兜底判不出来。
     * 故此处按可空处理，避免"提示文案缺失"升级成崩溃。
     */
    fun show(context: Context, text: String?, duration: Int = Toast.LENGTH_LONG) {
        if (text.isNullOrEmpty()) return
        post(context, text, duration)
    }

    /** 以字符串资源 ID 显示一条 Toast。 */
    fun show(context: Context, resId: Int, duration: Int) {
        show(context, LanguagePrefs.string(context, resId), duration)
    }

    /** 将 Toast 显示任务投递到主线程执行。 */
    private fun post(context: Context, text: String, duration: Int) {

        if (!globalEnabled) return
        // 用应用上下文，避免 Toast 持有并泄漏 Activity。
        val app = context.applicationContext
        mainHandler.post {
            // 排队期间开关可能已关，进入主线程后再检查一次。
            if (!globalEnabled) return@post
            try {
                Toast.makeText(app, text, duration).show()
            } catch (e: Exception) {

                // Toast 失败不崩溃，仅记录警告日志。
                Log.w(TAG, "Failed to show Toast: ${e.message}")
            }
        }
    }
}
