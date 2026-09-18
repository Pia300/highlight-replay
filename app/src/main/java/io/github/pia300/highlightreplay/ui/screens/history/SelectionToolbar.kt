package io.github.pia300.highlightreplay.ui.screens.history

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.pia300.highlightreplay.R

/**
 * 多选模式工具栏：退出、选中数量、全选/取消全选、批量打标签与批量删除。
 */
@Composable
internal fun SelectionToolbar(
    selectedCount: Int,
    allSelected: Boolean,
    onToggleAll: () -> Unit,
    onTagSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onExit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onExit) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.history_exit_selection)
            )
        }
        Text(
            text = stringResource(R.string.history_selected_count, selectedCount),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .weight(1f)
                // 选中数随点击变化，加实时区域让读屏主动播报。
                .semantics { liveRegion = LiveRegionMode.Polite },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        IconButton(onClick = onToggleAll) {
            Icon(
                if (allSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                contentDescription = if (allSelected)
                    stringResource(R.string.history_deselect_all)
                else
                    stringResource(R.string.history_select_all)
            )
        }
        IconButton(
            onClick = onTagSelected,
            enabled = selectedCount > 0
        ) {
            Icon(
                Icons.Outlined.Sell,
                contentDescription = stringResource(R.string.history_tag_selected),
                tint = if (selectedCount > 0) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(
            onClick = onDeleteSelected,
            enabled = selectedCount > 0
        ) {
            Icon(
                Icons.Outlined.DeleteOutline,
                contentDescription = stringResource(R.string.history_delete_selected),
                tint = if (selectedCount > 0) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
