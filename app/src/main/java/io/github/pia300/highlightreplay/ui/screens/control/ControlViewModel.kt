package io.github.pia300.highlightreplay.ui.screens.control

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.pia300.highlightreplay.service.RecorderService
import io.github.pia300.highlightreplay.service.RecorderState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 控制页状态与命令入口。
 *
 * 界面状态只由进程级 [RecorderService.stateFlow] 投影得出，界面不再自行复位状态；
 * 停止/保存命令经 startService 下发，与磁贴、通知路径共用同一条命令通道。
 */
class ControlViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        private const val TAG = "ControlViewModel"
    }

    private val _uiState = MutableStateFlow(RecorderService.currentState().toControlUiState())
    val uiState: StateFlow<ControlUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            RecorderService.stateFlow.collect { state ->
                _uiState.value = state.toControlUiState()
            }
        }
    }

    fun stopRecording() = sendCommand(RecorderService.ACTION_STOP)

    fun saveReplay() = sendCommand(RecorderService.ACTION_TRIGGER_REPLAY)

    private fun sendCommand(action: String) {
        val app = getApplication<Application>()
        try {
            app.startService(Intent(app, RecorderService::class.java).apply { this.action = action })
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send $action: ${e.message}")
        }
    }
}

private fun RecorderState.toControlUiState(): ControlUiState = ControlUiState(
    isRecording = isRunning,
    isSaving = isSaving,
    settingsChanged = settingsStale,
    elapsedSeconds = elapsedSeconds
)
