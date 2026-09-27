package io.github.pia300.highlightreplay.ui.screens.control

import io.github.pia300.highlightreplay.service.session.RecorderState
import io.github.pia300.highlightreplay.service.session.SessionPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlUiStateMappingTest {

    @Test
    fun idleStateMapsToReady() {
        val ui = RecorderState().toControlUiState()

        assertFalse(ui.isRecording)
        assertFalse(ui.isSaving)
        assertFalse(ui.settingsChanged)
        assertEquals(0L, ui.elapsedSeconds)
    }

    @Test
    fun recordingStateMapsIsRecordingAndElapsed() {
        val ui = RecorderState(phase = SessionPhase.RUNNING, elapsedSeconds = 42L).toControlUiState()

        assertTrue(ui.isRecording)
        assertFalse(ui.isSaving)
        assertEquals(42L, ui.elapsedSeconds)
    }

    @Test
    fun savingTailWindowKeepsIsSavingWithoutRecording() {
        val ui = RecorderState(phase = SessionPhase.SAVING).toControlUiState()

        assertFalse(ui.isRecording)
        assertTrue(ui.isSaving)
    }

    @Test
    fun startingStateStillMapsToRecording() {
        val ui = RecorderState(phase = SessionPhase.STARTING).toControlUiState()

        assertTrue(ui.isRecording)
        assertFalse(ui.isSaving)
    }

    @Test
    fun settingsStaleMapsToSettingsChanged() {
        val ui = RecorderState(phase = SessionPhase.RUNNING, settingsStale = true).toControlUiState()

        assertTrue(ui.isRecording)
        assertTrue(ui.settingsChanged)
    }
}
