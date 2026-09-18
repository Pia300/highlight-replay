package io.github.pia300.highlightreplay.ui.floating

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.pia300.highlightreplay.R
import io.github.pia300.highlightreplay.ui.theme.AudioBar
import io.github.pia300.highlightreplay.ui.theme.FloatingRecording
import io.github.pia300.highlightreplay.ui.theme.StatusCaptureLost
import io.github.pia300.highlightreplay.ui.theme.StatusCaptureLostDark
import io.github.pia300.highlightreplay.ui.theme.StatusCaptureLostDarkIcon

/** 悬浮覆盖层 UI 状态：录制中 / 画面丢失两种视觉。 */
data class FloatingOverlayState(
    val videoActive: Boolean = false,
    val amplitude: Float = 0f,
    val sizePx: Int,
    val opacityPercent: Int,
    /** 深色主题：由服务按应用主题设置解析后传入，避免在 composition 中读 SharedPreferences。 */
    val darkTheme: Boolean = false
)

/** 悬浮窗控制按钮：圆角背景、随音频电平变化的音频条与保存图标。 */
@Composable
fun FloatingControlOverlay(
    state: FloatingOverlayState,
    onSave: () -> Unit,
    onStopRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    // sizePx（像素）转 dp 供 Compose 布局使用。
    val density = LocalDensity.current
    val buttonSize: Dp = with(density) { state.sizePx.toDp() }
    val iconSize: Dp = with(density) { (state.sizePx * 0.5f).toInt().toDp() }

    val saveLabel = stringResource(R.string.control_save_replay)
    val stopLabel = stringResource(R.string.control_stop_recording)

    // 背景色在录制/丢失两态间平滑过渡；深色判定由服务下发，与主界面主题设置一致。
    val lostOnDark = !state.videoActive && state.darkTheme
    val background by animateColorAsState(
        targetValue = if (state.videoActive) FloatingRecording
        // 捕获丢失时按深色主题选用对应强调色。
        else if (lostOnDark) StatusCaptureLostDark else StatusCaptureLost,
        animationSpec = spring(),
        label = "backgroundColor"
    )

    // 图标按底色对比度选色：深绿/深红底用白；深色主题浅粉“丢失”底改用近黑深红。
    val iconColor = if (lostOnDark) StatusCaptureLostDarkIcon else Color.White

    // 音频电平动画：限制在 0..1 并平滑变化。
    val audioLevel by animateFloatAsState(
        targetValue = state.amplitude.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 1.2f, stiffness = Spring.StiffnessMediumLow),
        label = "audioLevel"
    )

    Box(
        modifier = modifier
            .size(buttonSize)
            .alpha(state.opacityPercent / 100f)
            // 无障碍：悬浮球是唯一的快捷入口，触摸由宿主 View 的 OnTouchListener 处理，
            // 必须在 Compose 语义树里暴露按钮角色、点击与长按动作，否则 TalkBack 无法发现/激活。
            .semantics(mergeDescendants = true) {
                contentDescription = saveLabel
                role = Role.Button
                onClick(label = saveLabel) {
                    onSave()
                    true
                }
                onLongClick(label = stopLabel) {
                    onStopRecording()
                    true
                }
            },
        contentAlignment = Alignment.Center
    ) {

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(background)
        )

        // 电平超过阈值才绘制音频条，避免无声时闪烁。
        if (audioLevel > 0.01f) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
            ) {
                val barHeight = size.height * audioLevel
                drawRect(
                    color = AudioBar,
                    topLeft = Offset(0f, size.height - barHeight),
                    size = Size(size.width, barHeight)
                )
            }
        }

        // 保存图标：悬浮球单击语义为“保存回放”（仅此单一图标态）。
        // 图标为装饰元素，contentDescription 置空以排除出无障碍树。
        Icon(
            imageVector = Icons.Filled.Save,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(iconSize)
        )
    }
}
