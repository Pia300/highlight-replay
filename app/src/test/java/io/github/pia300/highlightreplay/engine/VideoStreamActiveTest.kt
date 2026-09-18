package io.github.pia300.highlightreplay.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 对外展示判据（通知与悬浮球共用）的单元测试。
 *
 * 该判据取代了两处各自拼装的写法：通知侧曾是三合取，悬浮球侧只有
 * `hasRecentVideoOutput` 单项，导致排空线程死亡时通知报「中断」而悬浮球指示灯仍报「活跃」。
 */
class VideoStreamActiveTest {

    /** 三个合取项全为真才判为活跃。 */
    @Test
    fun allThreeConditionsAreRequired() {
        assertTrue(
            videoStreamActive(
                captureAlive = true,
                recentVideoOutput = true,
                encoderProgressing = true
            )
        )
    }

    /** 投影已被系统终止：无论渲染与帧数如何，一律判为不活跃。 */
    @Test
    fun captureNotAliveIsInactive() {
        assertFalse(
            videoStreamActive(
                captureAlive = false,
                recentVideoOutput = true,
                encoderProgressing = true
            )
        )
    }

    /** 渲染侧停摆（EGL 失败、渲染线程退出）：判为中断。 */
    @Test
    fun staleRenderIsInactive() {
        assertFalse(
            videoStreamActive(
                captureAlive = true,
                recentVideoOutput = false,
                encoderProgressing = true
            )
        )
    }

    /**
     * 排空线程死亡：渲染线程在输入缓冲填满前仍会持续提交并刷新渲染时刻，故渲染侧仍为活跃；
     * 只有帧数推进这一项能发现它。这是本判据存在的核心理由，也是旧写法漏掉的那一项。
     */
    @Test
    fun stalledEncoderIsInactiveEvenWhileRendering() {
        assertFalse(
            videoStreamActive(
                captureAlive = true,
                recentVideoOutput = true,
                encoderProgressing = false
            )
        )
    }

    /** 两项及以上为假时同样判为不活跃。 */
    @Test
    fun multipleFalseConditionsAreInactive() {
        assertFalse(
            videoStreamActive(
                captureAlive = false,
                recentVideoOutput = false,
                encoderProgressing = true
            )
        )
        assertFalse(
            videoStreamActive(
                captureAlive = true,
                recentVideoOutput = false,
                encoderProgressing = false
            )
        )
    }

    /**
     * 直连回退期间 `videoOutputActive` 恒为真（静止画面无法据此判定中断，见该函数 KDoc），
     * 因此判据的实际鉴别力完全落在帧数推进这一项上——组合后的行为必须是「帧数停滞即中断」。
     */
    @Test
    fun directConnectionLosesRenderSignalButKeepsProgressSignal() {
        val directConnection = true
        val recentVideoOutput = videoOutputActive(
            nowMillis = 10_000, timeoutMs = 500,
            projectionStopped = false,
            directConnection = directConnection,
            limiterRenderedTimeMs = null,
            lastFrameTimeMs = 0
        )
        assertTrue("直连回退下渲染侧恒判活跃（有意设计）", recentVideoOutput)

        assertTrue(
            "直连回退 + 帧数推进 => 活跃",
            videoStreamActive(true, recentVideoOutput, encoderProgressing = true)
        )
        assertFalse(
            "直连回退 + 帧数停滞 => 必须判为中断",
            videoStreamActive(true, recentVideoOutput, encoderProgressing = false)
        )
    }
}
