package io.github.pia300.highlightreplay.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioPtsClockTest {

    @Test
    fun startsAtZero() {
        val clock = AudioPtsClock(44100, 2)
        assertEquals(0L, clock.currentPtsUs())
        assertEquals(0L, clock.framesQueued)
        assertEquals(0L, clock.framesDropped)
    }

    @Test
    fun stereoConversionUsesChannelCount() {
        val clock = AudioPtsClock(44100, 2)
        clock.advanceQueued(4096)
        assertEquals(1024L, clock.framesQueued)
        assertEquals(1024L * 1_000_000L / 44100L, clock.currentPtsUs())
    }

    @Test
    fun monoConversionUsesSampleRateOnly() {
        val clock = AudioPtsClock(44100, 1)
        clock.advanceQueued(4096)
        assertEquals(2048L, clock.framesQueued)
        assertEquals(2048L * 1_000_000L / 44100L, clock.currentPtsUs())
    }

    @Test
    fun advancesMonotonically() {
        val clock = AudioPtsClock(44100, 2)
        clock.advanceQueued(4096)
        val first = clock.currentPtsUs()
        clock.advanceQueued(4096)
        val second = clock.currentPtsUs()
        assertEquals(2048L, clock.framesQueued)
        assertTrue(second > first)
    }

    @Test
    fun oddByteCountRoundsDownToWholeFrames() {
        val clock = AudioPtsClock(44100, 2)
        clock.advanceQueued(4095)
        assertEquals(1023L, clock.framesQueued)
    }

    @Test
    fun droppedBytesDoNotAdvanceTimeline() {
        val clock = AudioPtsClock(44100, 2)
        clock.advanceQueued(4096)
        val before = clock.currentPtsUs()
        clock.countDropped(8192)
        assertEquals(before, clock.currentPtsUs())
        assertEquals(2048L, clock.framesDropped)
        assertEquals(1024L, clock.framesQueued)
    }

    @Test
    fun resetClearsTimelineAndCounters() {
        val clock = AudioPtsClock(44100, 2)
        clock.advanceQueued(4096)
        clock.countDropped(4096)
        clock.reset()
        assertEquals(0L, clock.framesQueued)
        assertEquals(0L, clock.framesDropped)
        assertEquals(0L, clock.currentPtsUs())
    }
}
