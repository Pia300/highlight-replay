package io.github.pia300.highlightreplay.ui.screens.control

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.pia300.highlightreplay.R
import io.github.pia300.highlightreplay.ui.components.StatusIndicator

/** 状态指示器的默认边长（竖屏常规窗口；TopStatus 的默认参数）。 */
private val DEFAULT_INDICATOR_SIZE = 140.dp

/** 顶部状态区：状态指示器，以及录制中设置被改动时的提示横幅。 */
@Composable
internal fun TopStatus(
    uiState: ControlUiState,
    statusColor: Color,
    indicatorSize: Dp = DEFAULT_INDICATOR_SIZE
) {
    StatusIndicator(
        statusLabel = stringResource(uiState.statusLabelRes),
        statusSubLabel = stringResource(uiState.statusSubLabelRes),
        statusColor = statusColor,
        isActive = uiState.isRecording,
        size = indicatorSize
    )
    if (uiState.settingsChanged && uiState.isRecording) {
        Spacer(Modifier.height(12.dp))
        SettingsChangedBanner(modifier = Modifier.padding(horizontal = 12.dp))
    }
}

/** “录制中设置已更改”提示横幅：信息图标与说明文字。 */
@Composable
private fun SettingsChangedBanner(modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.control_settings_changed_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}
