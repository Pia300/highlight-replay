package io.github.pia300.highlightreplay.ui.screens.settings

import android.app.Application
import android.content.Intent
import android.content.SharedPreferences
import android.widget.Toast
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import io.github.pia300.highlightreplay.R
import io.github.pia300.highlightreplay.data.LanguagePrefs
import io.github.pia300.highlightreplay.data.RecorderPrefs
import io.github.pia300.highlightreplay.data.RecorderSettings
import io.github.pia300.highlightreplay.data.defaultPrefs
import io.github.pia300.highlightreplay.data.getBooleanSafe
import io.github.pia300.highlightreplay.data.getIntSafe
import io.github.pia300.highlightreplay.service.FloatingControlService
import io.github.pia300.highlightreplay.service.RecorderService
import io.github.pia300.highlightreplay.ui.ToastCenter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 设置页 ViewModel：读写应用主偏好文件并刷新状态流；录制中修改参数经静态入口通知录制服务（Toast + stale 标记，不热生效）。 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.defaultPrefs()

    private val mutableState: MutableStateFlow<SettingsUiState> =
        MutableStateFlow(readState())

    val state: StateFlow<SettingsUiState> = mutableState

    fun setStringPref(key: String, value: String) =
        editPref { putString(key, value) }

    fun setBoolPref(key: String, value: Boolean) =
        editPref { putBoolean(key, value) }

    fun setIntPref(key: String, value: Int) =
        editPref { putInt(key, value) }

    /** 三个同构 setter 的共用写入路径：一次 Editor 写入后整体重建状态流，去重由 UI 侧快路径负责。 */
    private fun editPref(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit { block() }
        mutableState.value = readState()
    }

    /** 录制中修改录制参数的进程内通知：弹提示并置 stale 标记（下次生效）。 */
    fun notifyRecordingSettingsChanged() {
        RecorderService.notifySettingsChangedWhileRecording()
    }

    /** 重置悬浮球设置：尺寸/透明度/位置即时生效，「录制时自动显示」下次会话生效。 */
    fun resetFloating() {
        prefs.edit {
            putInt(RecorderPrefs.KEY_FLOATING_SIZE, RecorderPrefs.FLOATING_SIZE_DEFAULT)
            putInt(RecorderPrefs.KEY_FLOATING_OPACITY, RecorderPrefs.FLOATING_OPACITY_DEFAULT)
            putBoolean(
                RecorderPrefs.KEY_AUTO_SHOW_FLOATING,
                RecorderPrefs.AUTO_SHOW_FLOATING_DEFAULT
            )
            remove(RecorderPrefs.KEY_FLOATING_X)
            remove(RecorderPrefs.KEY_FLOATING_Y)
        }
        notifyFloatingSettingsChanged(resetPosition = true)
        mutableState.value = readState()
        ToastCenter.show(
            getApplication(),
            LanguagePrefs.string(getApplication(), R.string.floating_reset_toast),
            Toast.LENGTH_SHORT
        )
    }

    /** 悬浮球外观设置变化：通知运行中的悬浮球服务即时应用。 */
    fun onFloatingSettingChanged() {
        notifyFloatingSettingsChanged()
    }

    private fun notifyFloatingSettingsChanged(resetPosition: Boolean = false) {
        if (!FloatingControlService.isRunning) return
        val app = getApplication<Application>()
        val intent = Intent(app, FloatingControlService::class.java).apply {
            action = FloatingControlService.ACTION_UPDATE_SETTINGS
            putExtra(FloatingControlService.EXTRA_RESET_POSITION, resetPosition)
        }
        app.startService(intent)
    }

    private fun readState(): SettingsUiState {
        val s = RecorderSettings.fromPreferences(getApplication())
        return SettingsUiState(
            codec = s.codec,
            encoderPreference = s.encoderPreference,
            resolution = s.resolution.toString(),
            resolutionMode = s.resolutionMode,
            frameRate = s.frameRate.toString(),
            bitrate = s.bitRate.toString(),
            orientation = s.orientation,
            audioSource = s.audioSource,
            replayDuration = s.replayDuration.toString(),
            toastNotify = s.toastNotify,
            contentRotation = s.contentRotation,
            audioMonitor = s.audioMonitor,
            language = LanguagePrefs.current(getApplication()),
            floatingSize = prefs.getIntSafe(
                RecorderPrefs.KEY_FLOATING_SIZE,
                RecorderPrefs.FLOATING_SIZE_DEFAULT
            ).coerceIn(RecorderPrefs.FLOATING_SIZE_MIN, RecorderPrefs.FLOATING_SIZE_MAX),
            floatingOpacity = prefs.getIntSafe(
                RecorderPrefs.KEY_FLOATING_OPACITY,
                RecorderPrefs.FLOATING_OPACITY_DEFAULT
            ).coerceIn(RecorderPrefs.FLOATING_OPACITY_MIN, RecorderPrefs.FLOATING_OPACITY_MAX),
            autoShowFloating = prefs.getBooleanSafe(
                RecorderPrefs.KEY_AUTO_SHOW_FLOATING,
                RecorderPrefs.AUTO_SHOW_FLOATING_DEFAULT
            )
        )
    }
}
