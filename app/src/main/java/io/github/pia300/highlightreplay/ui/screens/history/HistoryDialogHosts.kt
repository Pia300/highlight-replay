package io.github.pia300.highlightreplay.ui.screens.history

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.pia300.highlightreplay.R
import io.github.pia300.highlightreplay.data.TagCoverage
import io.github.pia300.highlightreplay.data.VideoTag
import io.github.pia300.highlightreplay.ui.components.TagBulkDialog
import io.github.pia300.highlightreplay.ui.components.TagEditDialog

/**
 * 历史页对话框宿主：旧版删除确认、重命名、批量标签与单个视频标签编辑。
 * 所有可见状态与回调均由调用方注入，此处只按参数渲染。
 */
@Composable
internal fun HistoryDialogHosts(
    legacyDeleteTargets: List<VideoItem>?,
    onLegacyDeleteConfirm: (List<VideoItem>) -> Unit,
    onLegacyDeleteDismiss: () -> Unit,
    renameTarget: VideoItem?,
    onRenameConfirm: (VideoItem, String) -> Unit,
    onRenameDismiss: () -> Unit,
    tagBulkOpen: Boolean,
    tagBulkTargetIds: List<Long>,
    allTags: List<VideoTag>,
    tagBulkSelectedIds: Set<String>,
    tagBulkCoverage: Map<String, TagCoverage>,
    onTagBulkToggleTag: (String) -> Unit,
    onTagBulkDeleteTag: (String) -> Unit,
    onTagBulkCreateTag: (String, Long) -> Boolean,
    onTagBulkDismiss: () -> Unit,
    tagBulkChipsEnabled: Boolean,
    tagEditTarget: VideoItem?,
    tagSelectedIds: Set<String>,
    onTagEditToggleTag: (VideoItem, String) -> Unit,
    onTagEditDeleteTag: (String) -> Unit,
    onTagEditCreateTag: (String, Long) -> Boolean,
    onTagEditDismiss: () -> Unit
) {
    legacyDeleteTargets?.let { targets ->
        AlertDialog(
            onDismissRequest = onLegacyDeleteDismiss,
            title = { Text(stringResource(R.string.history_delete_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.history_delete_confirm_message,
                        targets.size
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { onLegacyDeleteConfirm(targets) }) {
                    Text(
                        stringResource(R.string.history_menu_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onLegacyDeleteDismiss) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    renameTarget?.let { target ->
        RenameDialog(
            initialName = target.displayName.removeSuffix(MP4_EXTENSION),
            onConfirm = { newName -> onRenameConfirm(target, newName) },
            onDismiss = onRenameDismiss
        )
    }

    if (tagBulkOpen) {
        val coverageMap = tagBulkCoverage

        TagBulkDialog(
            title = stringResource(R.string.history_bulk_tag_title, tagBulkTargetIds.size),
            allTags = allTags,
            selectedTagIds = tagBulkSelectedIds,
            coverageOf = { tagId -> coverageMap[tagId] ?: TagCoverage.NONE },
            onToggleTag = onTagBulkToggleTag,
            onDeleteTag = onTagBulkDeleteTag,
            onCreateTag = onTagBulkCreateTag,
            onDismiss = onTagBulkDismiss,
            chipsEnabled = tagBulkChipsEnabled
        )
    }

    tagEditTarget?.let { target ->
        TagEditDialog(
            title = stringResource(
                R.string.history_edit_tag_title,
                target.displayName.removeSuffix(MP4_EXTENSION)
            ),
            allTags = allTags,
            selectedTagIds = tagSelectedIds,
            onToggleTag = { tagId -> onTagEditToggleTag(target, tagId) },
            onDeleteTag = onTagEditDeleteTag,
            onCreateTag = onTagEditCreateTag,
            onDismiss = onTagEditDismiss
        )
    }
}
