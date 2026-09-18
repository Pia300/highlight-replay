package io.github.pia300.highlightreplay.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.pia300.highlightreplay.R
import io.github.pia300.highlightreplay.data.TagCoverage
import io.github.pia300.highlightreplay.data.VideoTag

/**
 * 标签选择对话框（单选与批量编辑共用）：勾选/取消标签、长按请求删除、新建标签。
 * [coverageOf] 非空时标签块额外展示批量覆盖状态，否则只展示勾选态。
 * [chipsEnabled] 为 false 时禁用标签块交互（批量覆盖状态尚未加载完成）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TagPickerDialog(
    title: String,
    allTags: List<VideoTag>,
    selectedTagIds: Set<String>,
    coverageOf: ((String) -> TagCoverage)?,
    confirmLabel: String,
    onToggleTag: (String) -> Unit,
    onDeleteTag: (String) -> Unit,
    onCreateTag: (name: String, color: Long) -> Boolean,
    onDismiss: () -> Unit,
    chipsEnabled: Boolean = true
) {
    // 待确认删除的标签；非空时叠加删除确认对话框。
    var pendingDelete by remember { mutableStateOf<VideoTag?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(allTags, key = { it.id }) { tag ->
                        TagSelectChip(
                            tag = tag,
                            selected = tag.id in selectedTagIds,
                            coverage = coverageOf?.invoke(tag.id) ?: TagCoverage.NONE,
                            enabled = chipsEnabled,
                            onToggle = { onToggleTag(tag.id) },
                            onLongPress = {
                                // 长按请求删除（含默认标签）。
                                pendingDelete = tag
                            }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                CreateTagSection(onCreateTag = onCreateTag)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(confirmLabel)
            }
        }
    )

    pendingDelete?.let { tag ->
        DeleteTagConfirmDialog(
            tagName = tag.name,
            onConfirmDelete = {
                onDeleteTag(tag.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

/** 单个视频的标签编辑对话框。 */
@Composable
fun TagEditDialog(
    title: String,
    allTags: List<VideoTag>,
    selectedTagIds: Set<String>,
    onToggleTag: (String) -> Unit,
    onDeleteTag: (String) -> Unit,
    onCreateTag: (name: String, color: Long) -> Boolean,
    onDismiss: () -> Unit
) {
    TagPickerDialog(
        title = title,
        allTags = allTags,
        selectedTagIds = selectedTagIds,
        coverageOf = null,
        confirmLabel = stringResource(R.string.tag_close),
        onToggleTag = onToggleTag,
        onDeleteTag = onDeleteTag,
        onCreateTag = onCreateTag,
        onDismiss = onDismiss
    )
}

/**
 * 批量编辑对话框：为多段视频批量勾选/取消标签，标签块展示每标签的覆盖状态。
 * [chipsEnabled] 为 false 时禁用标签块，避免覆盖状态尚未加载完成就点击。
 */
@Composable
fun TagBulkDialog(
    title: String,
    allTags: List<VideoTag>,
    selectedTagIds: Set<String>,
    coverageOf: (String) -> TagCoverage,
    onToggleTag: (String) -> Unit,
    onDeleteTag: (String) -> Unit,
    onCreateTag: (name: String, color: Long) -> Boolean,
    onDismiss: () -> Unit,
    chipsEnabled: Boolean = true
) {
    TagPickerDialog(
        title = title,
        allTags = allTags,
        selectedTagIds = selectedTagIds,
        coverageOf = coverageOf,
        confirmLabel = stringResource(R.string.tag_done),
        onToggleTag = onToggleTag,
        onDeleteTag = onDeleteTag,
        onCreateTag = onCreateTag,
        onDismiss = onDismiss,
        chipsEnabled = chipsEnabled
    )
}

/** 删除标签前的二次确认对话框，删除按钮用错误色警示。 */
@Composable
private fun DeleteTagConfirmDialog(
    tagName: String,
    onConfirmDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tag_delete_title)) },
        text = { Text(stringResource(R.string.tag_delete_confirm, tagName)) },
        confirmButton = {
            TextButton(onClick = onConfirmDelete) {
                Text(stringResource(R.string.tag_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.tag_cancel))
            }
        }
    )
}
