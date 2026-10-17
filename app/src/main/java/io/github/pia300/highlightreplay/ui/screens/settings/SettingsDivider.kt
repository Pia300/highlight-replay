package io.github.pia300.highlightreplay.ui.screens.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 设置页细分隔线；insetStart 控制起点缩进。 */
@Composable
internal fun SettingsDivider(insetStart: Dp = 16.dp) {
    HorizontalDivider(
        modifier = Modifier.padding(start = insetStart, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    )
}
