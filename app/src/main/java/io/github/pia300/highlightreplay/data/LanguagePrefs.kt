package io.github.pia300.highlightreplay.data

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import java.util.Locale

/** 语言偏好管理：读取语言设置、构造指定语言 Context，并同步进程默认 Locale。 */
object LanguagePrefs {
    const val KEY_LANGUAGE = "language"
    const val LANG_SYSTEM = "system"
    const val LANG_ZH_CN = "zh-cn"
    const val LANG_ZH_TW = "zh-tw"
    const val LANG_EN = "en"
    const val LANG_JA = "ja"
    const val LANG_KO = "ko"
    const val LANG_ES = "es"
    const val LANG_PT = "pt"
    const val LANG_ID = "id"
    const val LANG_VI = "vi"
    const val LANG_TH = "th"
    const val LANG_RU = "ru"

    /** 兼容存储值 "zh"（中文简体）：读取时归一化为 [LANG_ZH_CN]，使该值仍能选中简体中文选项。 */
    private const val LEGACY_LANG_ZH = "zh"

    /** 当前语言代码；未设置时默认跟随系统；存储值 "zh" 归一化为简体中文。 */
    fun current(context: Context): String =
        normalize(
            // 末尾 ?: 仅为平台类型空安全补全（getStringSafe 已带默认值）。
            context.defaultPrefs().getStringSafe(KEY_LANGUAGE, LANG_SYSTEM) ?: LANG_SYSTEM
        )

    private fun normalize(language: String): String = when (language) {
        LEGACY_LANG_ZH -> LANG_ZH_CN
        else -> language
    }

    /** 语言代码映射为 Locale；未知值（跟随系统）返回 null。 */
    private fun resolveLocale(language: String): Locale? = when (language) {
        LANG_ZH_CN, LEGACY_LANG_ZH -> Locale.SIMPLIFIED_CHINESE
        LANG_ZH_TW -> Locale.TRADITIONAL_CHINESE
        LANG_EN -> Locale.ENGLISH
        LANG_JA -> Locale.JAPANESE
        LANG_KO -> Locale.KOREAN
        LANG_ES -> Locale.forLanguageTag("es")
        LANG_PT -> Locale.forLanguageTag("pt")
        LANG_ID -> Locale.forLanguageTag("id")
        LANG_VI -> Locale.forLanguageTag("vi")
        LANG_TH -> Locale.forLanguageTag("th")
        LANG_RU -> Locale.forLanguageTag("ru")
        else -> null
    }

    /** 用指定语言配置包裹 Context 以加载对应语言资源；语言不受支持时原样返回。 */
    fun wrap(context: Context, language: String): Context {
        val locale = resolveLocale(language) ?: return context
        val config = Configuration(context.resources.configuration).apply {
            setLocales(LocaleList(locale))
        }
        return context.createConfigurationContext(config)
    }

    /** 已包裹 Resources 的单槽缓存键与值：string() 在通知/Toast 路径高频调用，避免每次新建 ConfigurationContext。 */
    @Volatile
    private var cachedLang: String? = null

    @Volatile
    private var cachedResources: Resources? = null

    /** 按当前语言解析字符串资源，支持格式化参数。 */
    fun string(context: Context, resId: Int, vararg formatArgs: Any?): String =
        formatLocalizedString(resourcesFor(context, current(context)).getString(resId), formatArgs)

    /**
     * 无参返回原文，有参才格式化（[string] 的纯函数内核，便于单元测试覆盖两条分支）。
     *
     * 不可写成 `Resources.getString(id, *formatArgs)`：该变参重载会无条件执行 String.format，
     * 对含 `%1$s` 之类占位符的字符串以 0 个参数格式化会抛 MissingFormatArgumentException。
     */
    internal fun formatLocalizedString(raw: String, formatArgs: Array<out Any?>): String =
        if (formatArgs.isEmpty()) raw else String.format(raw, *formatArgs)

    /**
     * 取指定语言的 Resources：以应用 Context 为基（不持有界面 Context）。
     *
     * 缓存为单槽「最后写入者胜出」：`cachedLang` 与 `cachedResources` 是两个独立的 @Volatile 字段，
     * 语言并发切换的极短窗口内读者可能读到「新键 + 旧 Resources」，返回上一次语言的 Resources。
     * 缓存键把 `LANG_SYSTEM` 展开成系统当前 locale 列表，使系统语言变化时缓存立即失效。
     */
    private fun resourcesFor(context: Context, language: String): Resources {
        val cacheKey = effectiveCacheKey(language)
        cachedResources?.let { if (cachedLang == cacheKey) return it }
        val resources = wrap(context.applicationContext, language).resources
        cachedLang = cacheKey
        cachedResources = resources
        return resources
    }

    /** 缓存键：具体语言用其代码；跟随系统时展开为系统 locale 列表（系统语言变化即失效）。 */
    private fun effectiveCacheKey(language: String): String =
        if (language == LANG_SYSTEM) {
            Resources.getSystem().configuration.locales.toLanguageTags()
        } else {
            language
        }

    /** 将进程默认 Locale 同步为所选语言；全局副作用，影响所有依赖默认 Locale 的路径。 */
    fun syncDefaultLocale(language: String) {
        val locale = resolveLocale(language) ?: systemDefaultLocale()
        Locale.setDefault(locale)
    }

    private fun systemDefaultLocale(): Locale {
        val locales = android.content.res.Resources.getSystem().configuration.locales
        val firstTag = locales.toLanguageTags().substringBefore(',')
        return if (firstTag.isEmpty()) Locale.getDefault() else Locale.forLanguageTag(firstTag)
    }
}
