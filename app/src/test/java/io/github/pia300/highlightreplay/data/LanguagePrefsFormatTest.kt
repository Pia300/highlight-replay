package io.github.pia300.highlightreplay.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [LanguagePrefs.formatLocalizedString] 的行为约束：无参必须返回原文，有参才按占位符格式化。
 *
 * 该约束源自平台行为：`Resources.getString(id, *空数组)` 仍会执行 `String.format`，
 * 对含 `%1$s` 之类占位符的字符串以 0 个参数格式化会抛 `MissingFormatArgumentException`。
 */
class LanguagePrefsFormatTest {

    /** 无参数时必须原样返回，绝不调用 String.format。 */
    @Test
    fun noArgsReturnsRawTextWithoutFormatting() {
        val raw = "%1\$04d-%2\$02d-%3\$02d %4\$02d:%5\$02d"
        assertEquals(raw, LanguagePrefs.formatLocalizedString(raw, emptyArray()))

        val withPercent = "%1\$d ~ %2\$d%%"
        assertEquals(withPercent, LanguagePrefs.formatLocalizedString(withPercent, emptyArray()))
    }

    /** 有参数时按占位符格式化（含零填充与百分号转义）。 */
    @Test
    fun argsAreFormatted() {
        assertEquals("0007", LanguagePrefs.formatLocalizedString("%1\$04d", arrayOf<Any?>(7)))
        assertEquals(
            "2026-09-09 23:54",
            LanguagePrefs.formatLocalizedString(
                "%1\$04d-%2\$02d-%3\$02d %4\$02d:%5\$02d",
                arrayOf<Any?>(2026, 9, 9, 23, 54)
            )
        )
        assertEquals("20 ~ 100%", LanguagePrefs.formatLocalizedString("%1\$d ~ %2\$d%%", arrayOf<Any?>(20, 100)))
    }

    /** 无占位符的字符串传参时保持原样（不产生额外替换）。 */
    @Test
    fun plainTextStaysIntactWithArgs() {
        assertEquals("Replay", LanguagePrefs.formatLocalizedString("Replay", arrayOf<Any?>("ignored")))
    }
}
