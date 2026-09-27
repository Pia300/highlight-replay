package io.github.pia300.highlightreplay.service.session

data class RecorderState(
    val phase: SessionPhase = SessionPhase.IDLE,
    val elapsedSeconds: Long = 0L,
    val settingsStale: Boolean = false
) {

    val isRunning: Boolean get() = phase.isRunning

    val isSaving: Boolean get() = phase.isSaving

    val isCaptureReady: Boolean get() = phase.isCaptureReady

    init {
        require(phase != SessionPhase.IDLE || elapsedSeconds == 0L) {
            "无会话时不得残留已录时长：phase=$phase, elapsedSeconds=$elapsedSeconds"
        }
        require(phase != SessionPhase.IDLE || !settingsStale) {
            "无会话时不得残留设置变更提示：phase=$phase"
        }
    }
}
