package io.github.pia300.highlightreplay.ui.screens.control

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.pia300.highlightreplay.ui.components.isLandscapeLayout
import io.github.pia300.highlightreplay.ui.theme.LocalDarkTheme
import io.github.pia300.highlightreplay.ui.theme.StatusReady
import io.github.pia300.highlightreplay.ui.theme.StatusRecording
import io.github.pia300.highlightreplay.ui.theme.StatusRecordingDark
import io.github.pia300.highlightreplay.ui.theme.StatusReadyDark

/**
 * 控制屏幕主体：根据横竖屏与可用高度自适应排布状态指示、录制设置卡片和操作按钮。
 * 竖屏窗口过矮时使用紧凑布局；横屏时左侧为状态区、右侧为控制区。
 */
@Composable
fun ControlScreen(
    uiState: ControlUiState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onSaveReplay: () -> Unit,
    modifier: Modifier = Modifier
) {

    val darkTheme = LocalDarkTheme.current
    // 状态颜色：录制中（含保存中并存）用录制色；空闲（含保存收尾窗口）用就绪色。
    val statusColor = if (uiState.isRecording) {
        if (darkTheme) StatusRecordingDark else StatusRecording
    } else {
        if (darkTheme) StatusReadyDark else StatusReady
    }

    val isLandscape = isLandscapeLayout()

    // 横屏为左侧状态区 + 右侧控制区；竖屏纵向排列。
    if (isLandscape) {
        LandscapeControlLayout(
            uiState = uiState,
            statusColor = statusColor,
            onStartRecording = onStartRecording,
            onStopRecording = onStopRecording,
            onSaveReplay = onSaveReplay,
            modifier = modifier
        )
    } else {
        PortraitControlLayout(
            uiState = uiState,
            statusColor = statusColor,
            onStartRecording = onStartRecording,
            onStopRecording = onStopRecording,
            onSaveReplay = onSaveReplay,
            modifier = modifier
        )
    }
}
