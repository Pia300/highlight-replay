package io.github.pia300.highlightreplay.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** 设置分组的小节标题文本，带标题语义供读屏按标题导航。 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        // 标题四周内边距，与卡片内容对齐。
        modifier = modifier
            .semantics { heading() }
            .padding(

                start = 16.dp,
                end = 16.dp,
                top = 20.dp,
                bottom = 8.dp
            )
    )
}
