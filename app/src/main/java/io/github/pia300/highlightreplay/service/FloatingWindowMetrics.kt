package io.github.pia300.highlightreplay.service

import android.content.res.Resources
import android.graphics.Rect
import android.os.Build
import android.view.WindowManager

/**
 * 悬浮窗可停放区域：整块屏幕，不做任何系统栏内缩。
 *
 * 悬浮球允许停在屏幕任意位置，包括状态栏、导航栏与挖孔区域上方。
 * Android 11+ 用 maximumWindowMetrics，旧版回退 displayMetrics。
 */
internal fun getFloatingParkingBounds(windowManager: WindowManager?, resources: Resources): Rect {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val bounds = windowManager?.maximumWindowMetrics?.bounds
        if (bounds != null) return Rect(bounds)
    }
    // 旧版回退：displayMetrics 已按当前方向给出整块屏幕尺寸。
    @Suppress("DEPRECATION")
    val dm = resources.displayMetrics
    return Rect(0, 0, dm.widthPixels, dm.heightPixels)
}

/** 把悬浮窗位置夹取进边界；窗口大于边界时允许贴边。 */
internal fun clampFloatingPosition(bounds: Rect, sizePx: Int, x: Int, y: Int): Pair<Int, Int> {
    val size = sizePx.coerceAtLeast(0)
    val maxX = (bounds.right - size).coerceAtLeast(bounds.left)
    val maxY = (bounds.bottom - size).coerceAtLeast(bounds.top)
    return x.coerceIn(bounds.left, maxX) to y.coerceIn(bounds.top, maxY)
}
