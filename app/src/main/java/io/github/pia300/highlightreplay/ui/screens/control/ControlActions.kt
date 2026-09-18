package io.github.pia300.highlightreplay.ui.screens.control

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.pia300.highlightreplay.R
import io.github.pia300.highlightreplay.data.TimeFormat

/** 控制按钮的基准宽度，用于横屏布局及普通状态下按钮的宽度下限。 */
internal val CONTROL_BUTTON_WIDTH = 220.dp

/** 录制中显示已录时长文本，topSpacing 控制上方间距。 */
@Composable
internal fun RecordingDuration(uiState: ControlUiState, topSpacing: Dp = 24.dp) {
    if (uiState.isRecording) {
        Spacer(Modifier.height(topSpacing))
        val durationText = TimeFormat.formatDuration(uiState.elapsedSeconds)
        Text(
            text = stringResource(R.string.control_recording_duration, durationText),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 按录制状态渲染操作按钮组：未录制为“开始录制”；保存收尾窗口显示“保存中”并禁用；录制中为“保存回放/保存中”与“停止录制”。 */
@Composable
internal fun ControlButtons(
    uiState: ControlUiState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onSaveReplay: () -> Unit
) {

    // 录制中按钮占满整行；未录制时宽度限在基准宽到 320dp 之间。
    val buttonWidth = if (uiState.isRecording) {
        Modifier.fillMaxWidth()
    } else {
        Modifier.widthIn(min = CONTROL_BUTTON_WIDTH, max = 320.dp)
    }

    when {

        // 未录制：显示“开始录制”。保存收尾窗口（isSaving=true、isRecording=false）下
        // 改为“保存中”并禁用，避免新会话与在途保存冲突。
        !uiState.isRecording -> {
            Button(
                onClick = onStartRecording,
                modifier = buttonWidth,
                enabled = !uiState.isSaving,
                contentPadding = PaddingValues(horizontal = 24.dp)
            ) {
                Icon(
                    if (uiState.isSaving) Icons.Default.Save else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(
                        if (uiState.isSaving) R.string.control_saving
                        else R.string.control_start_recording
                    )
                )
            }
        }

        // 录制中：显示“保存回放/保存中”与“停止录制”。
        else -> {
            FilledTonalButton(
                onClick = onSaveReplay,
                modifier = buttonWidth,
                // 保存进行中禁用保存按钮，防止重复触发保存。
                enabled = !uiState.isSaving,
                contentPadding = PaddingValues(horizontal = 24.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(
                        if (uiState.isSaving) R.string.control_saving else R.string.control_save_replay
                    )
                )
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onStopRecording) {
                Text(
                    stringResource(R.string.control_stop_recording),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
