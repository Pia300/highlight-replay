package io.github.pia300.highlightreplay.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.edit
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.github.pia300.highlightreplay.data.RecorderPrefs
import io.github.pia300.highlightreplay.data.ThemePrefs
import io.github.pia300.highlightreplay.data.getIntSafe
import io.github.pia300.highlightreplay.ui.floating.FloatingControlOverlay
import io.github.pia300.highlightreplay.ui.floating.FloatingOverlayState
import io.github.pia300.highlightreplay.ui.theme.HighlightReplayTheme

/**
 * 悬浮球窗口：创建/更新/移除 overlay 视图，持有其 Compose 状态与拖动手势。
 *
 * @param context 服务上下文，用于取 WindowManager、资源与屏幕方向。
 * @param prefs 悬浮球偏好（尺寸、不透明度、位置）。
 * @param onTap 单击回调（保存回放）。
 * @param onLongPress 长按回调（停止会话）。
 * @param onAddFailed 添加视图失败时的回调（权限被撤销等）。
 */
internal class FloatingOverlayWindow(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val onTap: () -> Unit,
    private val onLongPress: () -> Unit,
    private val onAddFailed: () -> Unit
) {

    private companion object {
        private const val TAG = "FloatingControlService"
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var floatingView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    /** 手势控制器：持有待触发的长按回调，销毁时须取消。 */
    private var gestureController: FloatingGestureController? = null
    private var composeOwner: ServiceComposeOwner? = null

    /** 视图是否已挂载到 WindowManager。 */
    var isVisible = false
        private set

    // 视频输入活跃初值置 true（乐观），由帧活动广播按真实采样纠正，预热期不误报。
    private var videoActive by mutableStateOf(true)
    // 音频振幅（0..1），驱动音量指示。
    private var amplitude by mutableFloatStateOf(0f)
    private var floatingSizePx by mutableIntStateOf(0)
    private var opacityPercent by mutableIntStateOf(100)

    // 悬浮层主题：由宿主按应用主题设置解析成 Compose 状态，避免在 composition 中读 SharedPreferences。
    private var overlayDarkTheme by mutableStateOf(false)
    private var overlayColorMode by mutableStateOf(ThemePrefs.COLOR_DYNAMIC)

    /** 最近一次已知的屏幕方向：用于在宿主未收到 onConfigurationChanged 时兜底纠正位置。 */
    private var lastOrientation = context.resources.configuration.orientation

    /** 视图是否已创建（窗口丢失时为 false，需重建）。 */
    fun hasView(): Boolean = floatingView != null

    /** 创建并添加悬浮窗：初始化 Compose 内容、布局参数与触摸手势，恢复上次保存的位置。 */
    fun show() {

        // 自愈重建可能带着未销毁的旧宿主：先移除旧视图，再销毁其 Compose 宿主，
        // 否则已 DESTROYED 的 owner 下仍挂载 ComposeView（Compose 抛异常 + 窗口泄漏）。
        floatingView?.let { stale ->
            try {
                windowManager.removeView(stale)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to remove stale overlay view: ${e.message}")
            }
            floatingView = null
        }
        composeOwner?.destroy()
        composeOwner = ServiceComposeOwner()

        floatingSizePx = readFloatingSize()
        opacityPercent = readFloatingOpacity()
        overlayDarkTheme = resolveDarkTheme()
        overlayColorMode = ThemePrefs.themeColor(context)
        lastOrientation = context.resources.configuration.orientation

        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(composeOwner!!)
            setViewTreeSavedStateRegistryOwner(composeOwner!!)
            setViewTreeViewModelStoreOwner(composeOwner!!)
            setContent {
                HighlightReplayTheme(darkTheme = overlayDarkTheme, colorMode = overlayColorMode) {
                    FloatingControlOverlay(
                        state = FloatingOverlayState(
                            videoActive = videoActive,
                            amplitude = amplitude,
                            sizePx = floatingSizePx,
                            opacityPercent = opacityPercent,
                            darkTheme = overlayDarkTheme
                        ),
                        onSave = { onTap() },
                        onStopRecording = { onLongPress() }
                    )
                }
            }
        }
        floatingView = composeView

        // TYPE_APPLICATION_OVERLAY：Android 8.0+ 的系统级悬浮层。
        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // 不抢焦点，允许布局越出屏幕边界以贴边停靠。
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        val (clampedX, clampedY) = clampFloatingPosition(
            getFloatingParkingBounds(windowManager, context.resources),
            floatingSizePx,
            prefs.getIntSafe(RecorderPrefs.KEY_FLOATING_X, RecorderPrefs.FLOATING_POS_X_DEFAULT),
            prefs.getIntSafe(RecorderPrefs.KEY_FLOATING_Y, RecorderPrefs.FLOATING_POS_Y_DEFAULT)
        )
        layoutParams?.x = clampedX
        layoutParams?.y = clampedY

        setupTouchListeners()

        try {
            windowManager.addView(composeView, layoutParams)
            isVisible = true
        } catch (e: Exception) {
            // 添加失败（如权限被撤销）：清理资源并通知宿主停服。
            Log.e(TAG, "Failed to add overlay window", e)
            isVisible = false

            composeOwner?.destroy()
            floatingView = null
            composeOwner = null
            onAddFailed()
        }
    }

    /**
     * 读取最新设置并应用到悬浮窗（尺寸、不透明度、主题）。
     *
     * @param resetPosition 为 true 时把悬浮球移回偏好中的位置（重置外观流程）；否则保留当前坐标。
     *   窗口位置在拖动结束与越界纠正时已写回偏好，用偏好覆盖会丢失尚未落盘的位移。
     */
    fun refreshSettings(resetPosition: Boolean = false) {
        if (floatingView == null) return

        floatingSizePx = readFloatingSize()
        opacityPercent = readFloatingOpacity()
        overlayDarkTheme = resolveDarkTheme()
        overlayColorMode = ThemePrefs.themeColor(context)
        val lp = layoutParams ?: return

        if (resetPosition) {
            lp.x = prefs.getIntSafe(RecorderPrefs.KEY_FLOATING_X, RecorderPrefs.FLOATING_POS_X_DEFAULT)
            lp.y = prefs.getIntSafe(RecorderPrefs.KEY_FLOATING_Y, RecorderPrefs.FLOATING_POS_Y_DEFAULT)
        }
        try {
            windowManager.updateViewLayout(floatingView, lp)

            correctPositionIfOutOfBounds()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update overlay window", e)
            // 视图已不在 WindowManager（系统回收/权限撤销）：移除残留视图并按窗口丢失清理，自愈或停服由后续命令处理。
            try {
                windowManager.removeView(floatingView)
            } catch (_: Exception) {
            }
            isVisible = false
            floatingView = null
            composeOwner?.destroy()
            composeOwner = null
        }
    }

    /** 配置变化时重算尺寸并纠正越界位置。 */
    fun onConfigurationChanged() {
        floatingSizePx = readFloatingSize()
        // 尺寸变化可能使按钮移出屏幕，重新校正位置。
        floatingView?.post { correctPositionIfOutOfBounds() }
    }

    /**
     * 系统未把配置变化派发给宿主时按当前方向兜底：重算尺寸并在越界时夹回，
     * 保证旋转后悬浮球仍在可见区域内。
     */
    fun syncOrientationIfNeeded() {
        val orientation = context.resources.configuration.orientation
        if (orientation != lastOrientation) {
            lastOrientation = orientation
            floatingSizePx = readFloatingSize()
            floatingView?.post { correctPositionIfOutOfBounds() }
        }
    }

    /** 更新录制指示灯与音量指示：直接采用上游广播判定，不叠加本地延迟，与通知口径一致。 */
    fun updateIndicators(videoActive: Boolean, amplitude: Float) {

        this.amplitude = amplitude.coerceIn(0f, 1f)
        this.videoActive = videoActive
    }

    /** 退出前保存当前位置，便于下次启动恢复。 */
    fun savePosition() {
        layoutParams?.let { saveFloatingPosition(it.x, it.y) }
    }

    /** 移除视图、取消手势并销毁 Compose 宿主。 */
    fun destroy() {
        isVisible = false
        // 先取消待触发的长按回调，避免销毁后仍触发 startService/stopSelf。
        gestureController?.cancel()
        gestureController = null

        savePosition()

        try {
            // 视图可能已被系统移除，捕获异常防崩溃。
            floatingView?.let { windowManager.removeView(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove overlay window", e)
        }
        floatingView = null
        composeOwner?.destroy()
        composeOwner = null
    }

    /** 悬浮窗越界时夹回可见区域并保存新位置。 */
    private fun correctPositionIfOutOfBounds() {
        val lp = layoutParams ?: return
        val view = floatingView ?: return
        try {
            val (newX, newY) = clampFloatingPosition(
                getFloatingParkingBounds(windowManager, context.resources),
                floatingSizePx,
                lp.x,
                lp.y
            )
            if (newX != lp.x || newY != lp.y) {
                lp.x = newX
                lp.y = newY
                saveFloatingPosition(newX, newY)
                windowManager.updateViewLayout(view, lp)
                Log.d(TAG, "Overlay out-of-bounds corrected: ($newX, $newY)")
            }
        } catch (e: Exception) {
            // 视图可能已被移除，捕获异常防崩溃。
            Log.e(TAG, "Failed to correct overlay position", e)
        }
    }

    /** 将悬浮窗位置持久化到偏好设置。 */
    private fun saveFloatingPosition(x: Int, y: Int) {
        prefs.edit {
            putInt(RecorderPrefs.KEY_FLOATING_X, x)
            putInt(RecorderPrefs.KEY_FLOATING_Y, y)
        }
    }

    /** 绑定手势控制器：拖动、点击、长按与边界夹取。 */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchListeners() {
        val view = floatingView ?: return
        val controller = FloatingGestureController(
            touchView = view,
            touchSlop = ViewConfiguration.get(context).scaledTouchSlop,
            longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong(),
            layoutParams = { layoutParams },
            updatePosition = { newX, newY ->
                layoutParams?.x = newX
                layoutParams?.y = newY
                try {
                    windowManager.updateViewLayout(view, layoutParams)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update overlay position", e)
                }
            },
            clampPosition = { x, y ->
                clampFloatingPosition(
                    getFloatingParkingBounds(windowManager, context.resources),
                    floatingSizePx,
                    x,
                    y
                )
            },
            onDragEnd = { x, y -> saveFloatingPosition(x, y) },
            onTap = { onTap() },
            onLongPressAction = { onLongPress() }
        )
        gestureController = controller
        view.setOnTouchListener(controller)
    }

    /** 解析悬浮层深色主题：宿主上下文未被主题包裹，故用系统夜间标志 + 应用内主题设置。 */
    private fun resolveDarkTheme(): Boolean {
        val systemDark =
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
        return ThemePrefs.resolveDark(ThemePrefs.themeMode(context), systemDark)
    }

    /** 读取悬浮窗尺寸（dp），夹取到允许范围并转像素。 */
    private fun readFloatingSize(): Int {

        val sizeDp =
            prefs.getIntSafe(RecorderPrefs.KEY_FLOATING_SIZE, RecorderPrefs.FLOATING_SIZE_DEFAULT)
                .coerceIn(RecorderPrefs.FLOATING_SIZE_MIN, RecorderPrefs.FLOATING_SIZE_MAX)
        return (sizeDp * context.resources.displayMetrics.density).toInt()
    }

    /** 读取不透明度设置并夹取到允许范围。 */
    private fun readFloatingOpacity(): Int {
        return prefs.getIntSafe(
            RecorderPrefs.KEY_FLOATING_OPACITY,
            RecorderPrefs.FLOATING_OPACITY_DEFAULT
        )
            .coerceIn(RecorderPrefs.FLOATING_OPACITY_MIN, RecorderPrefs.FLOATING_OPACITY_MAX)
    }
}
