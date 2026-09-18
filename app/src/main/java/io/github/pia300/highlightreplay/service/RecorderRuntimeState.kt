package io.github.pia300.highlightreplay.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 进程级录制运行状态：磁贴/悬浮球/绑定方无服务实例时读取同一份数据。 */
internal object RecorderRuntimeState {

    @Volatile
    private var _isRunning = false
    @Volatile
    private var _isSaving = false

    val isRunning: Boolean get() = _isRunning
    val isSaving: Boolean get() = _isSaving

    /** 采集管线真正启动后置位（授权页据此保持前台；Android 15 无前台 Activity 时类型化 startForeground 会被降级并终止投影）。 */
    @Volatile
    var captureReady = false

    private val _stateFlow = MutableStateFlow(RecorderState())
    val stateFlow: StateFlow<RecorderState> = _stateFlow.asStateFlow()

    fun currentState(): RecorderState = _stateFlow.value

    /** 当前存活的 RecorderService 实例；服务销毁后置空。 */
    @Volatile
    var instance: RecorderService? = null

    /** 单一状态发布点：字段、StateFlow 与磁贴广播一次到位。 */
    fun publish(newState: RecorderState, notifyTile: Boolean, onTileUpdate: () -> Unit) {
        val runningChanged = _isRunning != newState.isRunning
        _isRunning = newState.isRunning
        _isSaving = newState.isSaving
        _stateFlow.value = newState
        if (notifyTile || runningChanged) onTileUpdate()
    }
}
