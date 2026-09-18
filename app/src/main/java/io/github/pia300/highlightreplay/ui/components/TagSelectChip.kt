package io.github.pia300.highlightreplay.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.pia300.highlightreplay.R
import io.github.pia300.highlightreplay.data.TagCoverage
import io.github.pia300.highlightreplay.data.VideoTag

/** 单个标签的选择块：展示勾选/批量覆盖状态，单击切换、长按删除；[enabled] 为 false 时不响应交互。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TagSelectChip(
    tag: VideoTag,
    selected: Boolean,
    coverage: TagCoverage = TagCoverage.NONE,
    enabled: Boolean = true,
    onToggle: () -> Unit,
    onLongPress: () -> Unit
) {
    // 勾选或批量覆盖非无即激活；部分覆盖用更淡的底色。
    val isActive = selected || coverage != TagCoverage.NONE
    val partial = coverage == TagCoverage.PARTIAL
    val deleteLabel = stringResource(R.string.tag_delete)
    val partialLabel = stringResource(R.string.tag_coverage_partial)
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = when {
            isActive -> Color(tag.color).copy(alpha = if (partial) 0.12f else 0.25f)
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (isActive) Color(tag.color) else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            // 无障碍：把芯片暴露为复选框并给出选中/部分选中状态，长按删除也带上操作名称。
            .semantics {
                role = Role.Checkbox
                // 显式 this：外层形参同名（selected），直接赋值会被解析为给 val 形参赋值。
                this.selected = isActive
                if (partial) stateDescription = partialLabel
            }
            .combinedClickable(
                enabled = enabled,
                onClick = onToggle,
                onLongClick = onLongPress,
                onLongClickLabel = deleteLabel
            )
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isActive) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = Color(tag.color),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
            }
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color(tag.color))
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = tag.name,
                style = MaterialTheme.typography.labelLarge,
                // 与只读标签一致：文字用标签色，颜色本身承担识别作用。
                // Surface 对非主题色推导不出 contentColor，须显式指定。
                color = Color(tag.color)
            )
            // 部分覆盖的短横线：表示标签只存在于部分选中视频上。
            if (partial) {
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(width = 12.dp, height = 2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant)
                )
            }
        }
    }
}
