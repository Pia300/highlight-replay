package io.github.pia300.highlightreplay.ui.screens.settings

import io.github.pia300.highlightreplay.data.RecorderSettings

/** 录制设置键到 UI 状态取值器的映射：供“值未变化则跳过写入”使用，仅覆盖经 update() 写入的录制设置。 */
internal enum class SettingsFieldKey(
    val prefKey: String,
    val valueOf: (SettingsUiState) -> String
) {
    CODEC(RecorderSettings.KEY_CODEC, { it.codec }),
    ENCODER_PREFERENCE(RecorderSettings.KEY_ENCODER_PREFERENCE, { it.encoderPreference }),
    RESOLUTION(RecorderSettings.KEY_RESOLUTION, { it.resolution }),
    RESOLUTION_MODE(RecorderSettings.KEY_RESOLUTION_MODE, { it.resolutionMode }),
    FRAME_RATE(RecorderSettings.KEY_FRAME_RATE, { it.frameRate }),
    BITRATE(RecorderSettings.KEY_BITRATE, { it.bitrate }),
    ORIENTATION(RecorderSettings.KEY_ORIENTATION, { it.orientation }),
    AUDIO_SOURCE(RecorderSettings.KEY_AUDIO_SOURCE, { it.audioSource }),
    REPLAY_DURATION(RecorderSettings.KEY_REPLAY_DURATION, { it.replayDuration }),
    TOAST_NOTIFY(RecorderSettings.KEY_TOAST_NOTIFY, { it.toastNotify }),
    CONTENT_ROTATION(RecorderSettings.KEY_CONTENT_ROTATION, { it.contentRotation }),
    AUDIO_MONITOR(RecorderSettings.KEY_AUDIO_MONITOR, { it.audioMonitor }),
    SHAKE_TO_SAVE(RecorderSettings.KEY_SHAKE_TO_SAVE, { it.shakeToSave }),
    SHAKE_STRENGTH(RecorderSettings.KEY_SHAKE_STRENGTH, { it.shakeStrength });

    companion object {
        /** 按存储键查找映射；未知键返回 null（调用方应只传本表内的键）。 */
        fun forStorageKey(prefKey: String): SettingsFieldKey? =
            entries.firstOrNull { it.prefKey == prefKey }
    }
}
