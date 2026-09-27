package io.github.pia300.highlightreplay.service.session

internal object SessionReducer {

    fun reduce(current: RecorderState, event: SessionEvent): RecorderState = when (event) {

        SessionEvent.StartRequested -> when (current.phase) {
            SessionPhase.IDLE -> RecorderState(phase = SessionPhase.STARTING)
            SessionPhase.SAVING -> RecorderState(phase = SessionPhase.STARTING_SAVING)
            else -> current
        }

        SessionEvent.CaptureReady -> when (current.phase) {
            SessionPhase.STARTING -> current.copy(phase = SessionPhase.RUNNING)
            SessionPhase.STARTING_SAVING -> current.copy(phase = SessionPhase.RUNNING_SAVING)
            else -> current
        }

        SessionEvent.SaveStarted ->
            if (current.phase == SessionPhase.RUNNING) {
                current.copy(phase = SessionPhase.RUNNING_SAVING)
            } else {
                current
            }

        SessionEvent.SaveFinished -> when (current.phase) {
            SessionPhase.RUNNING_SAVING -> current.copy(phase = SessionPhase.RUNNING)
            SessionPhase.STARTING_SAVING -> current.copy(phase = SessionPhase.STARTING)
            SessionPhase.SAVING -> RecorderState()
            else -> current
        }

        SessionEvent.StopRequested -> when (current.phase) {
            SessionPhase.STARTING -> RecorderState()
            SessionPhase.RUNNING -> RecorderState()
            SessionPhase.STARTING_SAVING -> RecorderState(phase = SessionPhase.SAVING)
            SessionPhase.RUNNING_SAVING -> RecorderState(phase = SessionPhase.SAVING)
            else -> current
        }

        is SessionEvent.ElapsedTick ->
            if (current.phase.isRunning) current.copy(elapsedSeconds = event.seconds) else current

        SessionEvent.SettingsChanged ->
            if (current.phase.isRunning) current.copy(settingsStale = true) else current
    }
}
