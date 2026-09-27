package io.github.pia300.highlightreplay.service.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionReducerTest {

    private fun reduce(from: RecorderState, event: SessionEvent) = SessionReducer.reduce(from, event)

    @Test
    fun startFromIdleEntersStarting() {
        val next = reduce(RecorderState(), SessionEvent.StartRequested)

        assertNotSame(RecorderState(), next)
        assertEquals(SessionPhase.STARTING, next.phase)
        assertTrue(next.isRunning)
        assertFalse(next.isSaving)
        assertFalse(next.isCaptureReady)
    }

    @Test
    fun captureReadyPromotesStartingToRunning() {
        val starting = RecorderState(phase = SessionPhase.STARTING)
        val next = reduce(starting, SessionEvent.CaptureReady)

        assertEquals(SessionPhase.RUNNING, next.phase)
        assertTrue(next.isRunning)
        assertTrue(next.isCaptureReady)
    }

    @Test
    fun elapsedTickUpdatesWhileStarting() {
        val starting = RecorderState(phase = SessionPhase.STARTING)
        val next = reduce(starting, SessionEvent.ElapsedTick(2L))

        assertEquals(2L, next.elapsedSeconds)
        assertEquals(SessionPhase.STARTING, next.phase)
    }

    @Test
    fun elapsedTickIsIgnoredWithoutSession() {
        val idle = RecorderState()

        assertSame(idle, reduce(idle, SessionEvent.ElapsedTick(5L)))
    }

    @Test
    fun elapsedTickUpdatesWhileRunning() {
        val running = RecorderState(phase = SessionPhase.RUNNING)
        val next = reduce(running, SessionEvent.ElapsedTick(9L))

        assertEquals(9L, next.elapsedSeconds)
        assertEquals(SessionPhase.RUNNING, next.phase)
    }

    @Test
    fun savingCoexistsWithRecording() {
        val running = RecorderState(phase = SessionPhase.RUNNING)
        val saving = reduce(running, SessionEvent.SaveStarted)

        assertEquals(SessionPhase.RUNNING_SAVING, saving.phase)
        assertTrue(saving.isRunning)
        assertTrue(saving.isSaving)
        assertTrue(saving.isCaptureReady)

        val finished = reduce(saving, SessionEvent.SaveFinished)
        assertEquals(SessionPhase.RUNNING, finished.phase)
        assertFalse(finished.isSaving)
    }

    @Test
    fun stopWhileSavingKeepsSavingWithoutRecording() {
        val saving = RecorderState(phase = SessionPhase.RUNNING_SAVING, elapsedSeconds = 30L)
        val next = reduce(saving, SessionEvent.StopRequested)

        assertEquals(SessionPhase.SAVING, next.phase)
        assertFalse(next.isRunning)
        assertTrue(next.isSaving)
        assertFalse(next.isCaptureReady)
        assertEquals(0L, next.elapsedSeconds)
    }

    @Test
    fun stopWhileRecordingReturnsToIdle() {
        val running = RecorderState(phase = SessionPhase.RUNNING, elapsedSeconds = 120L)
        val next = reduce(running, SessionEvent.StopRequested)

        assertEquals(SessionPhase.IDLE, next.phase)
        assertFalse(next.isRunning)
        assertFalse(next.isSaving)
        assertEquals(0L, next.elapsedSeconds)
    }

    @Test
    fun saveFinishedAfterStopReturnsToIdle() {
        val saving = RecorderState(phase = SessionPhase.SAVING)

        assertEquals(SessionPhase.IDLE, reduce(saving, SessionEvent.SaveFinished).phase)
    }

    @Test
    fun startWhilePreviousSessionSavingEntersStartingSaving() {
        val saving = RecorderState(phase = SessionPhase.SAVING)
        val next = reduce(saving, SessionEvent.StartRequested)

        assertEquals(SessionPhase.STARTING_SAVING, next.phase)
        assertTrue(next.isRunning)
        assertTrue(next.isSaving)
        assertFalse(next.isCaptureReady)
    }

    @Test
    fun saveFinishedWhileStartingKeepsSessionAlive() {
        val startingSaving = RecorderState(phase = SessionPhase.STARTING_SAVING, elapsedSeconds = 7L)
        val next = reduce(startingSaving, SessionEvent.SaveFinished)

        assertEquals(SessionPhase.STARTING, next.phase)
        assertTrue(next.isRunning)
        assertFalse(next.isSaving)
        assertEquals(7L, next.elapsedSeconds)
    }

    @Test
    fun captureReadyWhileStartingSavingKeepsSaving() {
        val startingSaving = RecorderState(phase = SessionPhase.STARTING_SAVING)
        val next = reduce(startingSaving, SessionEvent.CaptureReady)

        assertEquals(SessionPhase.RUNNING_SAVING, next.phase)
        assertTrue(next.isRunning)
        assertTrue(next.isSaving)
        assertTrue(next.isCaptureReady)
    }

    @Test
    fun saveStartedIsIgnoredWhileIdle() {
        val idle = RecorderState()

        assertSame(idle, reduce(idle, SessionEvent.SaveStarted))
    }

    @Test
    fun stopIsIgnoredWhileIdle() {
        val idle = RecorderState()

        assertSame(idle, reduce(idle, SessionEvent.StopRequested))
    }

    @Test
    fun startIsIgnoredWhileRunning() {
        val running = RecorderState(phase = SessionPhase.RUNNING)

        assertSame(running, reduce(running, SessionEvent.StartRequested))
    }

    @Test
    fun settingsChangedIsIgnoredWhileIdle() {
        val idle = RecorderState()

        assertSame(idle, reduce(idle, SessionEvent.SettingsChanged))
    }

    @Test
    fun settingsChangedIsRecordedWhileRunning() {
        val running = RecorderState(phase = SessionPhase.RUNNING)

        assertTrue(reduce(running, SessionEvent.SettingsChanged).settingsStale)
    }

    @Test
    fun startClearsPreviousSettingsStale() {
        val stale = RecorderState(phase = SessionPhase.RUNNING, settingsStale = true)
        val next = reduce(reduce(stale, SessionEvent.StopRequested), SessionEvent.StartRequested)

        assertEquals(SessionPhase.STARTING, next.phase)
        assertFalse(next.settingsStale)
    }

    @Test
    fun idleSnapshotRejectsResidualElapsed() {
        assertTrue(runCatching { RecorderState(phase = SessionPhase.IDLE, elapsedSeconds = 5L) }.isFailure)
    }

    @Test
    fun idleSnapshotRejectsResidualSettingsStale() {
        assertTrue(runCatching { RecorderState(phase = SessionPhase.IDLE, settingsStale = true) }.isFailure)
    }
}
