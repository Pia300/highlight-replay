package io.github.pia300.highlightreplay.ui.screens.control

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import io.github.pia300.highlightreplay.R

/**
 * 控制界面的不可变 UI 状态：录制/保存标志、已录时长与录制中设置变更提示。
 * isRecording 与 isSaving 相互独立：保存中可与录制并存，停止时若保存未结束会出现
 * 短暂的“未录制 + 保存中”收尾窗口，此时仍按“保存中”渲染并禁用开始按钮。
 */
@Immutable
data class ControlUiState(
    /** 是否正在录制。 */
    val isRecording: Boolean = false,
    /** 是否正在保存录制结果。 */
    val isSaving: Boolean = false,
    /** 录制中是否修改过设置（停止后重新录制才生效）。 */
    val settingsChanged: Boolean = false,
    /** 已录制时长（秒）。 */
    val elapsedSeconds: Long = 0L
) {
    /** 主/副标签资源 ID 对，由同一段私有 when 解析，两个公开 getter 共用同一来源。 */
    private val statusLabelPair: Pair<Int, Int>
        get() = when {
            // 优先级：保存中（含收尾窗口）> 录制中 > 就绪。
            isSaving ->
                R.string.control_status_saving to R.string.control_status_saving_sub
            isRecording ->
                R.string.control_status_recording to R.string.control_status_recording_sub
            else ->
                R.string.control_status_ready to R.string.control_status_ready_sub
        }

    /** 状态主标签的字符串资源 ID，随当前状态切换。 */
    @get:StringRes
    val statusLabelRes: Int
        get() = statusLabelPair.first

    /** 状态副标签的字符串资源 ID，与主标签优先级一致。 */
    @get:StringRes
    val statusSubLabelRes: Int
        get() = statusLabelPair.second
}
