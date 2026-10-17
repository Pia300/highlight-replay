package io.github.pia300.highlightreplay.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.pia300.highlightreplay.data.VideoTag
import io.github.pia300.highlightreplay.ui.screens.settings.SettingsDivider
import io.github.pia300.highlightreplay.ui.theme.HighlightReplayTheme
import io.github.pia300.highlightreplay.ui.theme.StatusReady
import io.github.pia300.highlightreplay.ui.theme.StatusRecording

/** 组件库的浅色/深色预览集合。 */
@Preview(name = "组件-浅色", showBackground = true, widthDp = 400)
@Composable
private fun ComponentPreviewsLight() {
    HighlightReplayTheme(darkTheme = false) {
        ComponentPreviewContent()
    }
}

/** 深色主题下的同一组组件。 */
@Preview(name = "组件-深色", showBackground = true, widthDp = 400)
@Composable
private fun ComponentPreviewsDark() {
    HighlightReplayTheme(darkTheme = true) {
        ComponentPreviewContent()
    }
}

/** 预览内容：设置分区、选择行、开关行与标签行。 */
@Composable
private fun ComponentPreviewContent() {
    Column {
        SettingSection(
            icon = SettingIcons.video,
            title = "视频设置"
        ) {
            SettingChoiceRow(
                icon = SettingIcons.resolution,
                title = "分辨率"
            ) {
                SettingChoiceControl(
                    options = listOf(
                        SettingChoice("720", "720p"),
                        SettingChoice("1080", "1080p"),
                        SettingChoice("1440", "1440p")
                    ),
                    selected = "1080",
                    onSelect = {}
                )
            }
            SettingsDivider(insetStart = 50.dp)
            SettingSwitchRow(
                icon = SettingIcons.contentRotation,
                title = "内容旋转",
                checked = true,
                onCheckedChange = {},
                hint = "录制中旋转屏幕时自动调整画面方向"
            )
        }
        SettingSection(
            icon = SettingIcons.theme,
            title = "主题"
        ) {
            SettingChoiceRow(
                icon = SettingIcons.darkMode,
                title = "深色模式"
            ) {
                SettingChoiceControl(
                    options = listOf(
                        SettingChoice("system", "跟随系统", SettingIcons.themeSystem),
                        SettingChoice("light", "浅色", SettingIcons.themeLight),
                        SettingChoice("dark", "深色", SettingIcons.themeDark)
                    ),
                    selected = "system",
                    onSelect = {}
                )
            }
        }
        TagChipRow(
            tags = listOf(
                VideoTag("tag_preview_1", "精彩瞬间", 0xFFE91E63),
                VideoTag("tag_preview_2", "待剪辑", 0xFF2196F3)
            ),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

/** 状态指示器的录制中与就绪两态。 */
@Preview(name = "状态指示器", showBackground = true, widthDp = 400)
@Composable
private fun StatusIndicatorPreview() {
    HighlightReplayTheme(darkTheme = false) {
        Column {
            StatusIndicator(
                statusLabel = "录制中",
                statusSubLabel = "回放缓冲持续写入",
                statusColor = StatusRecording,
                isActive = true
            )
            StatusIndicator(
                statusLabel = "就绪",
                statusSubLabel = "点击开始录制",
                statusColor = StatusReady,
                isActive = false
            )
        }
    }
}

/** 悬浮层按钮的深浅两态。 */
@Preview(name = "悬浮层按钮", showBackground = true, widthDp = 200)
@Composable
private fun FloatingOverlayPreview() {
    HighlightReplayTheme(darkTheme = true) {
        io.github.pia300.highlightreplay.ui.floating.FloatingControlOverlay(
            state = io.github.pia300.highlightreplay.ui.floating.FloatingOverlayState(
                videoActive = true,
                amplitude = 0.6f,
                sizePx = 144,
                opacityPercent = 90,
                darkTheme = true
            ),
            onSave = {},
            onStopRecording = {}
        )
    }
}
