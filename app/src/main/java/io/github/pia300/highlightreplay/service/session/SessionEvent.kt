package io.github.pia300.highlightreplay.service.session

internal sealed interface SessionEvent {

    data object StartRequested : SessionEvent

    data object CaptureReady : SessionEvent

    data object SaveStarted : SessionEvent

    data object SaveFinished : SessionEvent

    data object StopRequested : SessionEvent

    data class ElapsedTick(val seconds: Long) : SessionEvent

    data object SettingsChanged : SessionEvent
}
