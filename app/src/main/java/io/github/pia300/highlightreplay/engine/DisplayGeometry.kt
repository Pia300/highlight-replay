package io.github.pia300.highlightreplay.engine

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager

/** 屏幕方向与尺寸查询。 */
internal class DisplayGeometry(private val context: Context) {

    /**
     * 整块显示区域尺寸（含系统栏与挖孔区域）；Android 11+ 用 maximumWindowMetrics，旧版用 getRealMetrics。
     *
     * 两个 API 都返回“未被窗口装饰扣除”的完整区域，故尺寸不含导航栏扣减——采集需要整屏尺寸。
     */
    @Suppress("DEPRECATION")
    fun realDisplayMetrics(): DisplayMetrics {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val out = DisplayMetrics()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.maximumWindowMetrics.bounds

            out.setTo(context.resources.displayMetrics)
            // maximumWindowMetrics 在部分 ROM 上不随旋转变化：以资源方向为准校正宽高，
            // 保证自适应旋转与信箱缩放取到的是“当前方向”的尺寸。
            val landscape = context.resources.configuration.orientation ==
                    Configuration.ORIENTATION_LANDSCAPE
            val boundsLandscape = bounds.width() > bounds.height()
            val w = bounds.width()
            val h = bounds.height()
            out.widthPixels = if (landscape == boundsLandscape) w else h
            out.heightPixels = if (landscape == boundsLandscape) h else w
        } else {
            wm.defaultDisplay.getRealMetrics(out)
        }
        return out
    }
}
