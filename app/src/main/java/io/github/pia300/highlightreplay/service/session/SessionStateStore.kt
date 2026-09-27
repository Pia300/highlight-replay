package io.github.pia300.highlightreplay.service.session

import android.os.Looper
import android.util.Log
import androidx.annotation.MainThread
import androidx.annotation.VisibleForTesting
import io.github.pia300.highlightreplay.BuildConfig
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

internal object SessionStateStore {

    private const val TAG = "SessionStateStore"

    private val _snapshots = MutableStateFlow(RecorderState())
    val snapshots: StateFlow<RecorderState> = _snapshots.asStateFlow()

    private val _events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<SessionEvent> = _events.asSharedFlow()

    val snapshot: RecorderState get() = _snapshots.value

    @MainThread
    fun reduce(event: SessionEvent): RecorderState {
        assertMainThread(event)
        val current = _snapshots.value
        val next = SessionReducer.reduce(current, event)
        if (next == current) return current
        _snapshots.value = next
        _events.tryEmit(event)
        return next
    }

    @VisibleForTesting
    internal fun resetForTest() {
        _snapshots.value = RecorderState()
    }

    private fun assertMainThread(event: SessionEvent) {
        if (Looper.myLooper() === Looper.getMainLooper()) return
        val message = "会话状态只能在主线程发布：event=$event, thread=${Thread.currentThread().name}"
        if (BuildConfig.DEBUG) throw IllegalStateException(message) else Log.wtf(TAG, message)
    }
}
