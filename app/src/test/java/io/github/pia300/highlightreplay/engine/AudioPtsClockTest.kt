package io.github.pia300.highlightreplay.engine

import org.junit.Assert.assertEquals
import org.junit.Test

/** 音频时间轴换算的单元测试。 */
class AudioPtsClockTest {

    /** 未推进时 PTS 为 0。 */
    @Test
    fun startsAtZero() {
        val clock = AudioPtsClock(44100, 2)
        assertEquals(0L, clock.currentPtsUs())
        assertEquals(0L, clock.samplesFed)
    }

    /** 立体声下按声道数与采样率换算微秒。 */
    @Test
    fun stereoConversionUsesChannelCount() {
        val clock = AudioPtsClock(44100, 2)
        clock.advance(4096)
        assertEquals(2048L, clock.samplesFed)
        assertEquals(2048L * 1_000_000L / 88200L, clock.currentPtsUs())
    }

    /** 单声道下除数只含采样率。 */
    @Test
    fun monoConversionUsesSampleRateOnly() {
        val clock = AudioPtsClock(44100, 1)
        clock.advance(4096)
        assertEquals(2048L * 1_000_000L / 44100L, clock.currentPtsUs())
    }

    /** 时间轴随块累加单调前进。 */
    @Test
    fun advancesMonotonically() {
        val clock = AudioPtsClock(44100, 2)
        clock.advance(4096)
        val first = clock.currentPtsUs()
        clock.advance(4096)
        val second = clock.currentPtsUs()
        assertEquals(4096L, clock.samplesFed)
        org.junit.Assert.assertTrue(second > first)
    }

    /** 奇数字节数按完整样本取整推进。 */
    @Test
    fun oddByteCountRoundsDownToWholeSamples() {
        val clock = AudioPtsClock(44100, 2)
        clock.advance(4095)
        assertEquals(2047L, clock.samplesFed)
    }

    /** 重置回到起点。 */
    @Test
    fun resetClearsTimeline() {
        val clock = AudioPtsClock(44100, 2)
        clock.advance(4096)
        clock.reset()
        assertEquals(0L, clock.samplesFed)
        assertEquals(0L, clock.currentPtsUs())
    }
}
