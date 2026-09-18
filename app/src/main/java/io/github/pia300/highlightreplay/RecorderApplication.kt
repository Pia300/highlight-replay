package io.github.pia300.highlightreplay

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.SharedPreferences
import io.github.pia300.highlightreplay.data.LanguagePrefs
import io.github.pia300.highlightreplay.data.RecorderSettings
import io.github.pia300.highlightreplay.data.defaultPrefs
import io.github.pia300.highlightreplay.data.getStringSafe
import io.github.pia300.highlightreplay.data.thumbnailCache
import io.github.pia300.highlightreplay.service.NotificationFactory
import io.github.pia300.highlightreplay.ui.ToastCenter

/** 应用入口：语言包裹、通知渠道、Toast 全局开关的初始化与监听。 */
class RecorderApplication : Application() {

    // 监听 Toast 开关设置变化，实时启停全局 Toast。
    private val toastSwitchListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == RecorderSettings.KEY_TOAST_NOTIFY) {
                val prefs = this@RecorderApplication.defaultPrefs()
                ToastCenter.setGlobalEnabled(
                    prefs.getStringSafe(RecorderSettings.KEY_TOAST_NOTIFY, RecorderSettings.VALUE_ON)
                        != RecorderSettings.VALUE_OFF
                )
            }
        }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LanguagePrefs.wrap(base, LanguagePrefs.current(base)))
    }

    override fun onCreate() {
        super.onCreate()

        // 同步默认语言，保证新进程按相同语言解析字符串。
        LanguagePrefs.syncDefaultLocale(LanguagePrefs.current(this))
        // 创建通知渠道（Android 8.0+ 必需），否则通知不会显示。
        NotificationFactory.createChannel(this)

        val prefs = defaultPrefs()
        ToastCenter.setGlobalEnabled(
            prefs.getStringSafe(RecorderSettings.KEY_TOAST_NOTIFY, RecorderSettings.VALUE_ON)
                != RecorderSettings.VALUE_OFF
        )
        prefs.registerOnSharedPreferenceChangeListener(toastSwitchListener)
    }

    /** 系统内存紧张时清空缩略图缓存：进程级 LruCache 否则只在删除视频时清理。 */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            thumbnailCache.evictAll()
        }
    }
}
