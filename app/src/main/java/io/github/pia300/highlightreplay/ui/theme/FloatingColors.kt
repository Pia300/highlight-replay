package io.github.pia300.highlightreplay.ui.theme

import androidx.compose.ui.graphics.Color

/** 悬浮层“捕获已丢失”告警色（浅色底）：重导出共享基色（与浅色主题 error token 同值）。 */
val StatusCaptureLost = StatusCaptureLostBase

/** 悬浮层“捕获已丢失”告警色（深色底）：与深色主题 error token 同值，重导出共享基色。 */
val StatusCaptureLostDark = StatusCaptureLostDarkBase

/** 深色浅粉“丢失”底上的前景图标色（近黑深红保证对比度），与浅色主题 onErrorContainer 同值。 */
val StatusCaptureLostDarkIcon = StatusCaptureLostDarkIconBase

/** 悬浮窗音频电平条颜色（绿色），与状态指示 StatusRecordingDark 共享同一基色。 */
val AudioBar = StatusActiveGreenBase

/** 悬浮窗“录制中”状态颜色（深绿，仅本层使用，保留字面量）。 */
val FloatingRecording = Color(0xFF1B5E20)

