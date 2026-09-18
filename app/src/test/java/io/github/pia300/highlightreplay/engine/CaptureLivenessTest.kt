package io.github.pia300.highlightreplay.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 采集活性判定的单元测试。 */
class CaptureLivenessTest {

    /** 投影已停止时视频输出判为不活跃。 */
    @Test
    fun stoppedProjectionIsInactive() {
        assertFalse(
            videoOutputActive(
                nowMillis = 1_000, timeoutMs = 500,
                projectionStopped = true, directConnection = false,
                limiterRenderedTimeMs = 900, lastFrameTimeMs = 900
            )
        )
    }

    /** 直连回退路径恒判为活跃，静止画面不误报中断。 */
    @Test
    fun directConnectionIsAlwaysActive() {
        assertTrue(
            videoOutputActive(
                nowMillis = 10_000, timeoutMs = 500,
                projectionStopped = false, directConnection = true,
                limiterRenderedTimeMs = null, lastFrameTimeMs = 0
            )
        )
    }

    /** 限帧层最近渲染时刻在超时窗口内判为活跃。 */
    @Test
    fun recentLimiterRenderIsActive() {
        assertTrue(
            videoOutputActive(
                nowMillis = 1_000, timeoutMs = 500,
                projectionStopped = false, directConnection = false,
                limiterRenderedTimeMs = 600, lastFrameTimeMs = 0
            )
        )
    }

    /** 限帧层渲染时刻超出窗口判为断流。 */
    @Test
    fun staleLimiterRenderIsInactive() {
        assertFalse(
            videoOutputActive(
                nowMillis = 1_000, timeoutMs = 500,
                projectionStopped = false, directConnection = false,
                limiterRenderedTimeMs = 400, lastFrameTimeMs = 999
            )
        )
    }

    /** 未启用限帧层时以入缓冲时刻判定。 */
    @Test
    fun fallsBackToLastFrameTimeWithoutLimiter() {
        assertTrue(
            videoOutputActive(
                nowMillis = 1_000, timeoutMs = 500,
                projectionStopped = false, directConnection = false,
                limiterRenderedTimeMs = null, lastFrameTimeMs = 600
            )
        )
        assertFalse(
            videoOutputActive(
                nowMillis = 1_000, timeoutMs = 500,
                projectionStopped = false, directConnection = false,
                limiterRenderedTimeMs = null, lastFrameTimeMs = 400
            )
        )
    }

    /** 时间戳未初始化（0）判为不活跃。 */
    @Test
    fun zeroTimestampsAreInactive() {
        assertFalse(
            videoOutputActive(
                nowMillis = 1_000, timeoutMs = 500,
                projectionStopped = false, directConnection = false,
                limiterRenderedTimeMs = 0L, lastFrameTimeMs = 0L
            )
        )
        assertFalse(
            videoOutputActive(
                nowMillis = 1_000, timeoutMs = 500,
                projectionStopped = false, directConnection = false,
                limiterRenderedTimeMs = null, lastFrameTimeMs = 0L
            )
        )
    }

    /** 音频电平在超时窗口内判为有输入。 */
    @Test
    fun audioPeakInsideWindowIsActive() {
        assertTrue(audioPeakActive(nowMillis = 1_000, timeoutMs = 500, lastAudioPeakTimeMs = 600))
    }

    /** 音频电平超出窗口或未出现过判为无输入。 */
    @Test
    fun audioPeakOutsideWindowIsInactive() {
        assertFalse(audioPeakActive(nowMillis = 1_000, timeoutMs = 500, lastAudioPeakTimeMs = 400))
        assertFalse(audioPeakActive(nowMillis = 1_000, timeoutMs = 500, lastAudioPeakTimeMs = 0L))
    }

    /**
     * 帧数推进是排空线程存活的唯一外部可见证据：渲染活跃而帧数停滞即排空线程已死亡。
     */
    @Test
    fun encoderProgressRequiresFrameCountGrowth() {
        val tracker = EncoderProgressTracker(timeoutMs = 1_000)
        assertTrue(tracker.isProgressing(currentFrameCount = 1, nowMs = 0))
        assertTrue(tracker.isProgressing(currentFrameCount = 2, nowMs = 100))
        assertTrue(tracker.isProgressing(currentFrameCount = 3, nowMs = 200))
    }

    /** 帧数停滞时，基准时刻起的超时窗口内仍判为推进，超出窗口判为停滞。 */
    @Test
    fun encoderProgressToleratesStallInsideTimeoutWindow() {
        val tracker = EncoderProgressTracker(timeoutMs = 1_000)
        assertTrue(tracker.isProgressing(currentFrameCount = 10, nowMs = 5_000))
        assertTrue(tracker.isProgressing(currentFrameCount = 10, nowMs = 5_500))
        assertFalse(tracker.isProgressing(currentFrameCount = 10, nowMs = 6_000))
    }

    /** 长时间轮询下基准随每次增长前移，故停滞判定以最后一次增长为起点。 */
    @Test
    fun encoderProgressRebasesOnEveryAdvance() {
        val tracker = EncoderProgressTracker(timeoutMs = 1_000)
        assertTrue(tracker.isProgressing(currentFrameCount = 10, nowMs = 0))
        assertTrue(tracker.isProgressing(currentFrameCount = 11, nowMs = 900))
        assertTrue(tracker.isProgressing(currentFrameCount = 11, nowMs = 1_800))
        assertFalse(tracker.isProgressing(currentFrameCount = 11, nowMs = 1_900))
    }

    /**
     * 编码器帧计数每次会话启动归零：未复位基准时新会话的低计数相对旧会话高基准恒判为停滞。
     */
    @Test
    fun staleBaselineMarksNewSessionAsStalled() {
        val tracker = EncoderProgressTracker(timeoutMs = 1_000)
        assertTrue(tracker.isProgressing(currentFrameCount = 9_000, nowMs = 0))

        assertFalse(tracker.isProgressing(currentFrameCount = 1, nowMs = 60_000))
        assertFalse(tracker.isProgressing(currentFrameCount = 600, nowMs = 60_100))
    }

    /** 会话启动时复位基准，新会话从低帧数起即判为推进。 */
    @Test
    fun resetRestoresProgressDetectionForNewSession() {
        val tracker = EncoderProgressTracker(timeoutMs = 1_000)
        assertTrue(tracker.isProgressing(currentFrameCount = 9_000, nowMs = 0))

        tracker.reset()
        assertTrue(tracker.isProgressing(currentFrameCount = 1, nowMs = 60_000))
        assertTrue(tracker.isProgressing(currentFrameCount = 2, nowMs = 60_100))
    }
}
