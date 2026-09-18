package io.github.pia300.highlightreplay.ui.screens.control

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.pia300.highlightreplay.ui.components.RecordingSettingsCard

/** 横屏“窗口过矮”判定阈值（左右状态/控制区共用；低于该值改用可滚动紧凑布局）。 */
private val LANDSCAPE_SHORT_MAX_HEIGHT = 300.dp

/** 竖屏“小窗口”判定阈值（低于该值使用紧凑布局）。 */
private val PORTRAIT_SMALL_MAX_HEIGHT = 600.dp

/** 紧凑布局下状态指示器的边长（横屏矮窗与竖屏小窗共用）。 */
private val COMPACT_INDICATOR_SIZE = 96.dp

/** 常规横屏布局下状态指示器的边长。 */
private val LANDSCAPE_INDICATOR_SIZE = 112.dp

/** 非录制态显示的设置摘要卡及其后间距；录制期间不渲染。 */
@Composable
private fun SettingsSummary(
    isRecording: Boolean,
    bottomSpacing: Dp
) {
    if (isRecording) return
    RecordingSettingsCard(modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(bottomSpacing))
}

/** 横屏布局：左侧为状态区、右侧为控制区。 */
@Composable
internal fun LandscapeControlLayout(
    uiState: ControlUiState,
    statusColor: Color,
    settingsChanged: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onSaveReplay: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            // 横屏窗口过矮（低于 LANDSCAPE_SHORT_MAX_HEIGHT）时改用可滚动布局，避免内容溢出。
            if (maxHeight < LANDSCAPE_SHORT_MAX_HEIGHT) {

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TopStatus(
                        uiState = uiState,
                        statusColor = statusColor,
                        settingsChanged = settingsChanged,
                        indicatorSize = COMPACT_INDICATOR_SIZE
                    )
                }
            } else {

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    TopStatus(
                        uiState = uiState,
                        statusColor = statusColor,
                        settingsChanged = settingsChanged,
                        indicatorSize = LANDSCAPE_INDICATOR_SIZE
                    )
                }
            }
        }

        Spacer(Modifier.width(24.dp))

        BoxWithConstraints(
            modifier = Modifier
                .width(CONTROL_BUTTON_WIDTH)
                .fillMaxHeight()
        ) {
            if (maxHeight < LANDSCAPE_SHORT_MAX_HEIGHT) {

                Column(
                    modifier = Modifier.fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        SettingsSummary(
                            isRecording = uiState.isRecording,
                            bottomSpacing = 16.dp
                        )
                    }
                    ControlButtons(
                        uiState = uiState,
                        onStartRecording = onStartRecording,
                        onStopRecording = onStopRecording,
                        onSaveReplay = onSaveReplay,
                    )
                    RecordingDuration(uiState = uiState, topSpacing = 16.dp)
                }
            } else {

                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally

                ) {

                    SettingsSummary(isRecording = uiState.isRecording, bottomSpacing = 24.dp)
                    ControlButtons(
                        uiState = uiState,
                        onStartRecording = onStartRecording,
                        onStopRecording = onStopRecording,
                        onSaveReplay = onSaveReplay,
                    )

                    RecordingDuration(uiState = uiState)
                }
            }
        }
    }
}

/** 竖屏布局：状态区在上、设置卡片与操作按钮在下。 */
@Composable
internal fun PortraitControlLayout(
    uiState: ControlUiState,
    statusColor: Color,
    settingsChanged: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onSaveReplay: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // 竖屏高度低于 PORTRAIT_SMALL_MAX_HEIGHT 视为小窗口，改用紧凑布局。
        val smallWindow = maxHeight < PORTRAIT_SMALL_MAX_HEIGHT
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(if (smallWindow) 12.dp else 40.dp))
            // 小窗口紧凑布局：状态区可滚动，设置卡片靠底部对齐。
            if (smallWindow) {

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        TopStatus(
                            uiState = uiState,
                            statusColor = statusColor,
                            settingsChanged = settingsChanged,
                            indicatorSize = COMPACT_INDICATOR_SIZE
                        )
                    }
                    // 录制期间不显示设置摘要卡。
                    if (!uiState.isRecording) {
                        RecordingSettingsCard(
                            compact = true,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .widthIn(max = 300.dp)
                                .padding(bottom = 8.dp)
                        )
                    }
                }
                ControlButtons(
                    uiState = uiState,
                    onStartRecording = onStartRecording,
                    onStopRecording = onStopRecording,
                    onSaveReplay = onSaveReplay,
                )
                RecordingDuration(uiState = uiState, topSpacing = 12.dp)
                Spacer(Modifier.height(12.dp))
            } else {
                // 常规竖屏窗口：状态区占满剩余高度（超高可滚动），卡片与按钮固定在底部。
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        TopStatus(
                            uiState = uiState,
                            statusColor = statusColor,
                            settingsChanged = settingsChanged
                        )
                    }
                }
                if (!uiState.isRecording) {
                    RecordingSettingsCard(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(24.dp))
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ControlButtons(
                        uiState = uiState,
                        onStartRecording = onStartRecording,
                        onStopRecording = onStopRecording,
                        onSaveReplay = onSaveReplay,
                    )
                    RecordingDuration(uiState = uiState)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
