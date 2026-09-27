package io.github.pia300.highlightreplay.service.session

enum class SessionPhase {

    IDLE,

    STARTING,

    STARTING_SAVING,

    RUNNING,

    RUNNING_SAVING,

    SAVING;

    val isRunning: Boolean
        get() = this == STARTING || this == STARTING_SAVING ||
            this == RUNNING || this == RUNNING_SAVING

    val isSaving: Boolean
        get() = this == STARTING_SAVING || this == RUNNING_SAVING || this == SAVING

    val isCaptureReady: Boolean
        get() = this == RUNNING || this == RUNNING_SAVING
}
