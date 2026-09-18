package io.github.pia300.highlightreplay.data

/** 悬浮球相关偏好键与默认值（全部存放于应用主偏好文件 [defaultPrefs]）。 */
object RecorderPrefs {

    // 手动隐藏位：由通知上的悬浮球开关翻转，跨会话记忆“用户明确隐藏过”。
    const val KEY_FLOATING_HIDDEN = "floating_hidden"

    // 会话开始时是否自动显示悬浮球（设置页开关）；需与手动隐藏位同向且授权悬浮窗权限才显示。
    const val KEY_AUTO_SHOW_FLOATING = "auto_show_floating"
    const val AUTO_SHOW_FLOATING_DEFAULT = true

    // 外观与位置。
    const val KEY_FLOATING_SIZE = "floating_size"
    const val KEY_FLOATING_OPACITY = "floating_opacity"
    const val KEY_FLOATING_X = "floating_position_x"
    const val KEY_FLOATING_Y = "floating_position_y"

    // 悬浮球尺寸（dp，运行时按密度换算像素）。下限取 32dp：更小的命中区无法稳定点按与拖动。
    const val FLOATING_SIZE_DEFAULT = 33
    const val FLOATING_SIZE_MIN = 32
    const val FLOATING_SIZE_MAX = 100

    // 悬浮球透明度（百分比）。下限取 35%：更低时悬浮球难以辨识。
    const val FLOATING_OPACITY_DEFAULT = 50
    const val FLOATING_OPACITY_MIN = 35
    const val FLOATING_OPACITY_MAX = 100

    // 悬浮球默认位置（像素，运行时与屏幕尺寸比较并夹回屏内）。
    const val FLOATING_POS_X_DEFAULT = 50
    const val FLOATING_POS_Y_DEFAULT = 200
}
