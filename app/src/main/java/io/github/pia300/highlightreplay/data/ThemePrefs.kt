package io.github.pia300.highlightreplay.data

import android.content.Context
import androidx.core.content.edit

/** 主题模式与主题色偏好的 SharedPreferences 读写。 */
object ThemePrefs {
    const val KEY_THEME_MODE = "theme_mode"
    const val KEY_THEME_COLOR = "theme_color"

    const val MODE_SYSTEM = "system"
    const val MODE_LIGHT = "light"
    const val MODE_DARK = "dark"

    const val COLOR_DYNAMIC = "dynamic"
    const val COLOR_BLUE = "blue"
    const val COLOR_GREEN = "green"
    const val COLOR_PURPLE = "purple"
    const val COLOR_ORANGE = "orange"

    /** 当前主题模式；未设置时缺省为跟随系统。 */
    fun themeMode(context: Context): String =
        context.defaultPrefs().getStringSafe(KEY_THEME_MODE, MODE_SYSTEM) ?: MODE_SYSTEM

    /** 当前主题色；未设置时缺省为动态取色。 */
    fun themeColor(context: Context): String =
        context.defaultPrefs().getStringSafe(KEY_THEME_COLOR, COLOR_DYNAMIC) ?: COLOR_DYNAMIC

    /** 解析是否使用深色主题：显式模式优先，否则取系统深色标志。 */
    fun resolveDark(mode: String, systemDark: Boolean): Boolean = when (mode) {
        MODE_LIGHT -> false
        MODE_DARK -> true
        else -> systemDark
    }

    /** 保存主题模式；非法取值被忽略。 */
    fun setThemeMode(context: Context, mode: String) {

        if (mode !in THEME_MODES) return
        context.defaultPrefs().edit { putString(KEY_THEME_MODE, mode) }
    }

    /** 保存主题色；非法取值被忽略。 */
    fun setThemeColor(context: Context, color: String) {
        if (color !in THEME_COLORS) return
        context.defaultPrefs().edit { putString(KEY_THEME_COLOR, color) }
    }

    private val THEME_MODES = setOf(MODE_SYSTEM, MODE_LIGHT, MODE_DARK)
    private val THEME_COLORS =
        setOf(COLOR_DYNAMIC, COLOR_BLUE, COLOR_GREEN, COLOR_PURPLE, COLOR_ORANGE)
}
