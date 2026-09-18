package io.github.pia300.highlightreplay.ui.screens.history

import io.github.pia300.highlightreplay.R
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.Locale

/** 历史列表文本格式化的单元测试。 */
class HistoryItemFormatterTest {

    /** 记录每次资源请求的参数。 */
    private val calls = mutableListOf<Pair<Int, List<Any?>>>()

    /** 资源解析器返回值，日期用例需要它是可用的格式串。 */
    private val pattern = "%1\$04d-%2\$02d-%3\$02d %4\$02d:%5\$02d"

    private val resolver: StringResolver = { id, args ->
        calls += id to args.toList()
        pattern
    }

    /** 达到 1MB 及以上走 MB 分支。 */
    @Test
    fun sizeTextUsesMegabytesAboveOneMegabyte() {
        HistoryItemFormatter.sizeText(resolver, 3L * 1024 * 1024)
        assertEquals(R.string.history_size_mb, calls.single().first)
        assertEquals(3f, calls.single().second.single() as Float, 1e-6f)
    }

    /** 1KB 到 1MB 之间走 KB 分支。 */
    @Test
    fun sizeTextUsesKilobytesBetweenOneKilobyteAndOneMegabyte() {
        HistoryItemFormatter.sizeText(resolver, 2048L)
        assertEquals(R.string.history_size_kb, calls.single().first)
        assertEquals(2f, calls.single().second.single() as Float, 1e-6f)
    }

    /** 1KB 以下走字节分支，参数为原始字节数。 */
    @Test
    fun sizeTextUsesBytesBelowOneKilobyte() {
        HistoryItemFormatter.sizeText(resolver, 512L)
        assertEquals(R.string.history_size_b, calls.single().first)
        assertEquals(512L, calls.single().second.single())
    }

    /** 不足 60 秒走“秒”资源，参数为整秒数。 */
    @Test
    fun durationTextUsesSecondsBelowAMinute() {
        HistoryItemFormatter.durationText(resolver, 12_500L)
        assertEquals(R.string.history_duration_seconds, calls.single().first)
        assertEquals(12L, calls.single().second.single())
    }

    /** 达到 60 秒走分:秒格式，不再请求资源。 */
    @Test
    fun durationTextUsesTimeFormatFromAMinute() {
        val text = HistoryItemFormatter.durationText(resolver, 125_000L)
        assertEquals("02:05", text)
        assertEquals(0, calls.size)
    }

    /** 日期按“年-月-日 时:分”格式化，月份为 1 起算。 */
    @Test
    fun dateTextFormatsCalendarFields() {
        val dateAdded = 1_700_000_000L
        val cal = Calendar.getInstance().apply { timeInMillis = dateAdded * 1000 }
        val expected = String.format(
            Locale.US,
            pattern,
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE)
        )

        assertEquals(expected, HistoryItemFormatter.dateText(resolver, dateAdded))
        assertEquals(R.string.history_date_format, calls.single().first)
        assertEquals(0, calls.single().second.size)
    }
}
