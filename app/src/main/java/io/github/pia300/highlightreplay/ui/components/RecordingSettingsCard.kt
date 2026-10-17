package io.github.pia300.highlightreplay.ui.components

import android.content.SharedPreferences
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.pia300.highlightreplay.R
import io.github.pia300.highlightreplay.data.AudioSourceMode
import io.github.pia300.highlightreplay.data.RecorderSettings
import io.github.pia300.highlightreplay.data.ResolutionMode
import io.github.pia300.highlightreplay.data.VideoCodec
import io.github.pia300.highlightreplay.data.defaultPrefs
import io.github.pia300.highlightreplay.ui.SettingOptionLabels

/**
 * 录制设置摘要卡片：一行“分辨率/模式/编码”主摘要，下面 2×2 排列其余四项。
 *
 * 原实现是五行“图标 + 标签 + 值”，卡片高、扫读路径长；这里把分辨率模式、
 * 分辨率与编码合并成一行主摘要（保留原有全部信息），其余数值改成等宽两列，
 * 卡片更矮、层次更清楚，也不再依赖五个图标撑节奏。
 *
 * 横屏右栏只有 220dp：用 BoxWithConstraints 收紧内边距与字号，并把主摘要改为
 * 上下排，避免值被截断；小窗（compact）复用同一套密集样式。
 */
@Composable
fun RecordingSettingsCard(
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val context = LocalContext.current
    // 从偏好加载录制设置。initialValue 处于实参位置，每次重组都会求值，故置空由 producer 首帧读取。
    val settings by produceState<RecorderSettings?>(initialValue = null) {
        /** 从当前偏好重新读取设置并更新状态。 */
        fun refresh() {
            value = RecorderSettings.fromPreferences(context)
        }
        refresh()
        val prefs = context.defaultPrefs()
        // 注册偏好变化监听器，任意键变化即触发刷新。
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refresh() }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        // 组合离开组合树时注销监听器，防止泄漏。
        awaitDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val settingsValue = settings ?: return

    val resolutionLabelRes = SettingOptionLabels.resolution(settingsValue.resolution)
    val resolutionLabel = if (resolutionLabelRes != null) {
        stringResource(resolutionLabelRes)
    } else {
        stringResource(
            R.string.settings_resolution_custom,
            settingsValue.resolution
        )
    }
    val codecLabel = when (settingsValue.codecEnum) {
        VideoCodec.H264 -> stringResource(R.string.settings_codec_h264)
        VideoCodec.H265 -> stringResource(R.string.settings_codec_h265)
    }
    val resolutionModeLabel = when (settingsValue.resolutionModeEnum) {
        ResolutionMode.ADAPTIVE -> stringResource(R.string.settings_resolution_mode_adaptive)
        ResolutionMode.STANDARD -> stringResource(R.string.settings_resolution_mode_standard)
    }
    val audioLabel = when (settingsValue.audioMode) {
        AudioSourceMode.INTERNAL -> stringResource(R.string.settings_audio_internal)
        AudioSourceMode.NONE -> stringResource(R.string.settings_audio_none)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )
    ) {
        BoxWithConstraints {
            val narrow = maxWidth < 280.dp
            val dense = compact || narrow
            val contentPadding = if (dense) 12.dp else 16.dp
            val blockSpacing = if (dense) 8.dp else 12.dp
            val cellSpacing = if (dense) 8.dp else 16.dp
            val cellValueStyle = when {
                narrow -> MaterialTheme.typography.bodySmall
                compact -> MaterialTheme.typography.bodyMedium
                else -> MaterialTheme.typography.titleSmall
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentPadding)
            ) {
                // 主摘要：分辨率模式 · 分辨率 · 编码，保留原卡片里被合并的三项信息。
                if (dense) {
                    Column {
                        Text(
                            text = stringResource(R.string.settings_resolution),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(
                                R.string.settings_resolution_summary,
                                resolutionModeLabel,
                                resolutionLabel,
                                codecLabel
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = if (narrow) 2 else 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.settings_resolution),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(
                                R.string.settings_resolution_summary,
                                resolutionModeLabel,
                                resolutionLabel,
                                codecLabel
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.height(blockSpacing))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(blockSpacing))

                Row(horizontalArrangement = Arrangement.spacedBy(cellSpacing)) {
                    SettingInfoCell(
                        label = stringResource(R.string.settings_frame_rate),
                        value = stringResource(R.string.settings_fps, settingsValue.frameRate),
                        valueStyle = cellValueStyle,
                        modifier = Modifier.weight(1f)
                    )
                    SettingInfoCell(
                        label = stringResource(R.string.settings_video_bitrate),
                        value = stringResource(R.string.settings_mbps, settingsValue.bitRate),
                        valueStyle = cellValueStyle,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(if (dense) 6.dp else 10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(cellSpacing)) {
                    SettingInfoCell(
                        label = stringResource(R.string.settings_audio_source),
                        value = audioLabel,
                        valueStyle = cellValueStyle,
                        modifier = Modifier.weight(1f)
                    )
                    SettingInfoCell(
                        label = stringResource(R.string.settings_replay_duration),
                        value = stringResource(
                            R.string.settings_seconds,
                            settingsValue.replayDuration
                        ),
                        valueStyle = cellValueStyle,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/** 规格格：上方小标签、下方当前值，两列等宽对齐。 */
@Composable
private fun SettingInfoCell(
    label: String,
    value: String,
    valueStyle: TextStyle,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = valueStyle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
