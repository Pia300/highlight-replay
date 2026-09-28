package io.github.pia300.highlightreplay.ui.screens.settings

import androidx.compose.runtime.Immutable

/** 设置页 UI 状态：录制设置与悬浮球设置的只读快照（字符串编码，与偏好存储格式一致）。 */
@Immutable
class SettingsUiState(
    val codec: String,
    val encoderPreference: String,
    val resolution: String,
    val resolutionMode: String,
    val frameRate: String,
    val bitrate: String,
    val orientation: String,
    val audioSource: String,
    val replayDuration: String,
    val toastNotify: String,
    val contentRotation: String,
    val audioMonitor: String,
    val shakeToSave: String,
    val shakeStrength: String,
    val language: String,
    val floatingSize: Int,
    val floatingOpacity: Int,
    val autoShowFloating: Boolean
)
