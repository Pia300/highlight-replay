package io.github.pia300.highlightreplay.ui.components

import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.pia300.highlightreplay.R
import io.github.pia300.highlightreplay.data.AudioSourceMode
import io.github.pia300.highlightreplay.data.RecorderSettings
import io.github.pia300.highlightreplay.data.ResolutionMode
import io.github.pia300.highlightreplay.data.VideoCodec
import io.github.pia300.highlightreplay.data.defaultPrefs
import io.github.pia300.highlightreplay.ui.SettingOptionLabels

/** 录制设置卡片：以图标加文本行展示当前录制配置摘要。 */
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

    val contentPadding = if (compact) 10.dp else 16.dp
    val rowSpacing = if (compact) 4.dp else 8.dp
    val iconSize = if (compact) 20.dp else 24.dp
    val textStyle =
        if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium

    // 用 surfaceContainerLow 作卡片背景，与主题一致。
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(

            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()

                .padding(contentPadding)
        ) {
            SettingInfoRow(
                Icons.Default.Videocam,
                stringResource(R.string.settings_resolution),

                stringResource(
                    R.string.settings_resolution_summary,
                    resolutionModeLabel, resolutionLabel, codecLabel
                ),
                iconSize = iconSize,
                textStyle = textStyle
            )
            Spacer(Modifier.height(rowSpacing))
            SettingInfoRow(
                Icons.Default.Speed,
                stringResource(R.string.settings_frame_rate),
                stringResource(R.string.settings_fps, settingsValue.frameRate),
                iconSize = iconSize,
                textStyle = textStyle
            )
            Spacer(Modifier.height(rowSpacing))
            SettingInfoRow(
                Icons.Default.HighQuality,
                stringResource(R.string.settings_video_bitrate),
                stringResource(R.string.settings_mbps, settingsValue.bitRate),
                iconSize = iconSize,
                textStyle = textStyle
            )
            Spacer(Modifier.height(rowSpacing))
            SettingInfoRow(
                Icons.Default.Mic,
                stringResource(R.string.settings_audio_source),
                audioLabel,
                iconSize = iconSize,
                textStyle = textStyle
            )
            Spacer(Modifier.height(rowSpacing))
            SettingInfoRow(
                Icons.Default.AccessTime,
                stringResource(R.string.settings_replay_duration),
                stringResource(R.string.settings_seconds, settingsValue.replayDuration),
                iconSize = iconSize,
                textStyle = textStyle
            )
        }
    }
}

/** 单行设置信息：左侧图标与标签，右侧当前值。 */
@Composable
private fun SettingInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    textStyle: TextStyle? = null
) {
    val effectiveTextStyle = textStyle ?: MaterialTheme.typography.bodyMedium
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 图标装饰性（标签已说明含义），无需无障碍描述。
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(iconSize)
            )
            Spacer(Modifier.size(12.dp))
            Text(
                text = label,
                style = effectiveTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = value,
            style = effectiveTextStyle,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
