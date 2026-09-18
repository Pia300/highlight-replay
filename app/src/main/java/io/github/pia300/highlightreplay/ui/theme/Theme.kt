package io.github.pia300.highlightreplay.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import io.github.pia300.highlightreplay.data.ThemePrefs

/** 向下传递当前深色主题标志，供自定义组件读取。 */
val LocalDarkTheme = staticCompositionLocalOf { false }

/**
 * 应用主题入口：根据深色模式与颜色模式选择动态或静态配色，
 * 排版与形状保持 Material 3 默认值。
 */
@Composable
fun HighlightReplayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorMode: String = ThemePrefs.COLOR_DYNAMIC,
    content: @Composable () -> Unit
) {
    // Android 12+（S）且动态模式时用系统壁纸动态颜色，否则退回静态配色。
    val colorScheme = if (colorMode == ThemePrefs.COLOR_DYNAMIC &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    ) {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        staticColorScheme(colorMode, darkTheme)
    }

    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}

/** 按颜色模式与深色标志返回对应静态 ColorScheme，未知模式回退蓝色主题。 */
fun staticColorScheme(colorMode: String, darkTheme: Boolean): ColorScheme = when (colorMode) {
    ThemePrefs.COLOR_BLUE ->
        if (darkTheme) DarkStaticColors else LightStaticColors

    ThemePrefs.COLOR_GREEN ->
        if (darkTheme) DarkGreenColors else LightGreenColors

    ThemePrefs.COLOR_PURPLE ->
        if (darkTheme) DarkPurpleColors else LightPurpleColors

    ThemePrefs.COLOR_ORANGE ->
        if (darkTheme) DarkOrangeColors else LightOrangeColors

    else -> if (darkTheme) DarkStaticColors else LightStaticColors
}
