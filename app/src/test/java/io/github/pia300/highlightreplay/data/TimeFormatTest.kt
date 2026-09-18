package io.github.pia300.highlightreplay.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** TimeFormat.formatDuration 的单元测试。 */
class TimeFormatTest {

    /** 验证 1 小时以内的时长按 MM:SS 格式化。 */
    @Test
    fun formatsSecondsAndMinutes() {
        assertEquals("00:00", TimeFormat.formatDuration(0))
        assertEquals("00:59", TimeFormat.formatDuration(59))
        assertEquals("01:00", TimeFormat.formatDuration(60))
        assertEquals("12:34", TimeFormat.formatDuration(754))
    }

    /** 验证达到 1 小时后输出包含小时部分，分钟与秒仍按两位补齐。 */
    @Test
    fun formatsHours() {
        assertEquals("1:00:00", TimeFormat.formatDuration(3600))
        assertEquals("1:02:03", TimeFormat.formatDuration(3723))
    }

    /** 验证负输入被钳制为 0，输出 00:00。 */
    @Test
    fun negativeInputClampedToZero() {

        assertEquals("00:00", TimeFormat.formatDuration(-1))
        assertEquals("00:00", TimeFormat.formatDuration(-999))
    }

    /** 验证 59:59 与 1:00:01 附近的小时格式切换正确。 */
    @Test
    fun hourBoundaries() {

        assertEquals("59:59", TimeFormat.formatDuration(3599))
        assertEquals("1:00:01", TimeFormat.formatDuration(3601))
        assertEquals("1:01:01", TimeFormat.formatDuration(3661))
    }
}
