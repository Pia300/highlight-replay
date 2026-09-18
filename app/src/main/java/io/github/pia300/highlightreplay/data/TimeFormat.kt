package io.github.pia300.highlightreplay.data

import java.util.Locale

/** 时间格式化工具：将秒数转为可读的时长文本。 */
object TimeFormat {

    /** 将总秒数转为时长文本，超一小时时含小时部分。 */
    fun formatDuration(totalSeconds: Long): String {
        // 负值按 0 处理，避免显示负数时间。
        val s = totalSeconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, sec)
        else String.format(Locale.getDefault(), "%02d:%02d", m, sec)
    }
}
