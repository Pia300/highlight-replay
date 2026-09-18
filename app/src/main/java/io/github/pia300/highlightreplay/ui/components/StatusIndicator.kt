package io.github.pia300.highlightreplay.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.style.TextAlign
import io.github.pia300.highlightreplay.R

/** 激活（录制中）状态下的放大比例。 */
private const val ACTIVE_SCALE = 1.06f

/** 状态指示器：中央状态圆、光环与主副标题，展示当前录制状态。 */
@Composable
fun StatusIndicator(
    statusLabel: String,
    statusSubLabel: String,
    statusColor: Color,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 140.dp
) {
    // 状态色平滑过渡，避免切换时跳变。
    val animatedColor by animateColorAsState(
        targetValue = statusColor,
        animationSpec = spring(),
        label = "statusColor"
    )
    // 激活时放大至 ACTIVE_SCALE 倍，否则恢复原尺寸。
    val scale by animateFloatAsState(
        targetValue = if (isActive) ACTIVE_SCALE else 1f,
        animationSpec = spring(),
        label = "statusScale"
    )
    // 光环透明度：激活 0.35，非激活 0（隐藏）。
    val ringAlpha by animateFloatAsState(
        targetValue = if (isActive) 0.35f else 0f,
        animationSpec = spring(),
        label = "ringAlpha"
    )

    // 主副标题合并为无障碍描述，供 TalkBack 朗读。
    val statusDescription =
        stringResource(R.string.control_status_combined, statusLabel, statusSubLabel)

    // clearAndSetSemantics 清除子树语义、只保留一条合成描述，避免与可见文本重复播报；其余元素无可读语义。
    Column(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = statusDescription
        },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(size)
                // 缩放值在绘制阶段读取：置于组合期会让整个指示器逐帧重组。
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            contentAlignment = Alignment.Center
        ) {

            Canvas(Modifier.size(size)) {
                val strokeWidth = size.toPx() * 0.07f
                drawCircle(
                    color = animatedColor.copy(alpha = ringAlpha),
                    // 半径按 ACTIVE_SCALE 折算，使光环放大后仍贴合布局框外缘；与内盘之间留出底色间隙。
                    // -0.5f 为抵消亚像素取整细缝的经验微调（px 单位）。
                    radius = size.toPx() / 2f / ACTIVE_SCALE - strokeWidth / 2f - 0.5f,
                    style = Stroke(width = strokeWidth)
                )
            }
            Box(
                modifier = Modifier
                    .size(size * 0.71f)
                    // 仅内盘填实心色；外圈由上方光环单独绘制。
                    .drawBehind {
                        drawCircle(animatedColor)
                    }
            ) {

                Canvas(
                    Modifier
                        .size(size * 0.2f)
                        .align(Alignment.Center)
                ) {
                    drawCircle(
                        // 中心点按状态色亮度取反：深色模式下状态色偏亮，纯白点对比度不足（几乎看不见）。
                        color = if (animatedColor.luminance() > 0.5f) {
                            Color.Black.copy(alpha = 0.72f)
                        } else {
                            Color.White
                        },
                        radius = this.size.minDimension / 2
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = statusLabel,

            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = statusSubLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
