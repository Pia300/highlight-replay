package io.github.pia300.highlightreplay.ui.screens.control

import io.github.pia300.highlightreplay.service.RecorderState
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
        val ui = RecorderState(isRunning = true, elapsedSeconds = 42L).toControlUiState()

        assertTrue(ui.isRecording)
        assertFalse(ui.isSaving)
        assertEquals(42L, ui.elapsedSeconds)
    }

    @Test
    fun savingTailWindowKeepsIsSavingWithoutRecording() {
        val ui = RecorderState(isRunning = false, isSaving = true).toControlUiState()

        assertFalse(ui.isRecording)
        assertTrue(ui.isSaving)
    }

    @Test
    fun settingsStaleMapsToSettingsChanged() {
        val ui = RecorderState(isRunning = true, settingsStale = true).toControlUiState()

        assertTrue(ui.isRecording)
        assertTrue(ui.settingsChanged)
    }
}
