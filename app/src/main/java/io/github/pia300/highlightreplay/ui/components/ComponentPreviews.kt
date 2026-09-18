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

/** 预览内容：小节标题、开关行、分隔线与标签行。 */
@Composable
private fun ComponentPreviewContent() {
    Column {
        SectionHeader(title = "视频设置")
        SwitchSettingRow(
            label = "内容旋转",
            boxText = "跟随屏幕方向旋转画面",
            checked = true,
            onCheckedChange = {},
            hint = "录制中旋转屏幕时自动调整画面方向"
        )
        SwitchSettingRow(
            label = "Toast 通知",
            boxText = "显示录制状态提示",
            checked = false,
            onCheckedChange = {},
            offHint = "关闭后不再显示状态提示"
        )
        SettingsDivider()
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
