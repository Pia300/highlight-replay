package io.github.pia300.highlightreplay.ui.theme

import androidx.compose.ui.graphics.Color

// 悬浮层与状态指示的共享基色：独立的固定配色层，不随动态取色（Material You）或主题切换漂移。
// “捕获已丢失”三色与 Material error / onErrorContainer token 同值，重复取值是有意的。

/** “捕获已丢失”告警色（浅色底），与浅色主题 error token 同值。 */
internal val StatusCaptureLostBase = Color(0xFFBA1A1A)

/** “捕获已丢失”告警色（深色底），与深色主题 error token 同值。 */
internal val StatusCaptureLostDarkBase = Color(0xFFFFB4AB)

/** 深色“捕获已丢失”浅粉底上的前景图标色，与浅色主题 onErrorContainer 同值。 */
internal val StatusCaptureLostDarkIconBase = Color(0xFF410002)

/** “活跃”浅绿：状态指示深色“录制中”与悬浮窗音频电平条 AudioBar 共用。 */
internal val StatusActiveGreenBase = Color(0xFF81C784)

/** 深色“就绪”浅蓝：深色方案无对应主色 token，固定取值保证对比度。 */
internal val StatusReadyBlueBase = Color(0xFF64B5F6)
