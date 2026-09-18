package io.github.pia300.highlightreplay.ui.screens.history

import io.github.pia300.highlightreplay.R
import io.github.pia300.highlightreplay.data.TimeFormat
import java.util.Calendar
import java.util.Locale

/** 字符串资源解析器：按资源 ID 与格式化参数返回本地化文本。 */
internal typealias StringResolver = (Int, Array<Any?>) -> String

/**
 * 把视频的原始数值格式化为历史列表展示文本。
 *
 * 文本统一经 [resolve] 取资源：Application 的 baseContext 只在进程启动时包裹一次语言，
 * 直接用 `Context.getString` 会在应用内切换语言后继续返回旧语言（直到进程被回收）。
 */
internal object HistoryItemFormatter {

    /** 字节数 → 本地化的 B/KB/MB 文本。 */
    fun sizeText(resolve: StringResolver, sizeBytes: Long): String = when {
        sizeBytes >= 1024 * 1024 ->
            resolve(R.string.history_size_mb, arrayOf(sizeBytes / 1024f / 1024f))

        sizeBytes >= 1024 -> resolve(R.string.history_size_kb, arrayOf(sizeBytes / 1024f))
        else -> resolve(R.string.history_size_b, arrayOf(sizeBytes))
    }

    /** 毫秒时长 → 本地化的“秒”或“分:秒”文本。 */
    fun durationText(resolve: StringResolver, durationMs: Long): String {
        val total = durationMs / 1000
        return if (total < 60) resolve(R.string.history_duration_seconds, arrayOf(total))
        else TimeFormat.formatDuration(total)
    }

    /** 秒级 Unix 时间戳 → 本地化的日期时间文本。 */
    fun dateText(resolve: StringResolver, dateAdded: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = dateAdded * 1000 }
        val year: Int = cal.get(Calendar.YEAR)
        // Calendar 月份从 0 开始，故加 1。
        val month: Int = cal.get(Calendar.MONTH) + 1
        val day: Int = cal.get(Calendar.DAY_OF_MONTH)
        val hour: Int = cal.get(Calendar.HOUR_OF_DAY)
        val minute: Int = cal.get(Calendar.MINUTE)
        return String.format(
            Locale.US,
            resolve(R.string.history_date_format, emptyArray()),
            year, month, day, hour, minute
        )
    }
}
