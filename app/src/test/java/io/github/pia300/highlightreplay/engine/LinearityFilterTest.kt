package io.github.pia300.highlightreplay.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [LinearityFilter] 的去重力行为：静止时归零、快变保留（这正是摇一摇判定依赖的分离）。 */
class LinearityFilterTest {

    private val out = FloatArray(3)

    /** 首帧以自身为重力基准：输出为零，不把整段重力当成一次冲击。 */
    @Test
    fun firstSampleProducesZeroLinear() {
        val filter = LinearityFilter()
        filter.toLinear(0f, 0f, 9.81f, out)
        assertEquals(0f, out[0], 0.001f)
        assertEquals(0f, out[1], 0.001f)
        assertEquals(0f, out[2], 0.001f)
    }

    /** 静止时重力被完全扣除，后续采样输出恒为 0。 */
    @Test
    fun staticGravityIsFullyRemoved() {
        val filter = LinearityFilter()
        filter.toLinear(0f, 0f, 9.81f, out)
        repeat(100) {
            filter.toLinear(0f, 0f, 9.81f, out)
            assertEquals(0f, out[0], 0.001f)
            assertEquals(0f, out[1], 0.001f)
            assertEquals(0f, out[2], 0.001f)
        }
    }

    /** 5Hz 的快速振荡基本原样通过（低通只跟得上缓慢的重力，不吞掉摇动）。 */
    @Test
    fun rapidOscillationPassesThrough() {
        val filter = LinearityFilter()
        var peak = 0f
        for (t in 0L..1000L step 20L) {
            val value = 9.81f + 20f * sin(2.0 * PI * 5.0 * t / 1000.0).toFloat()
            filter.toLinear(0f, 0f, value, out)
            peak = maxOf(peak, abs(out[2]))
        }
        assertTrue("快速振荡应保留大部分幅度，实测峰值 $peak", peak > 8f)
    }

    /** 复位后重新建立基准：复位前算出的重力不会残留。 */
    @Test
    fun resetRebuildsGravityBaseline() {
        val filter = LinearityFilter()
        filter.toLinear(0f, 0f, 9.81f, out)
        filter.reset()
        // 复位后换个姿态：首帧仍直接作为新基准，输出为零。
        filter.toLinear(0f, 9.81f, 0f, out)
        assertEquals(0f, out[1], 0.001f)
    }
}
