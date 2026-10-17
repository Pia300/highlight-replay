package io.github.pia300.highlightreplay.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 应用统一形状基线。
 *
 * Material 3 默认值（4/8/12/16/28）偏紧凑，卡片在浅色低对比表面下会显得生硬；
 * 这里整体放大一级，让卡片、指标格与缩略图共享同一套圆角语言。
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)
