package io.github.pia300.highlightreplay.ui.screens.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.pia300.highlightreplay.R
import io.github.pia300.highlightreplay.ui.components.SearchField

/**
 * 历史页顶栏：搜索框与排序菜单，仅在非选择模式下显示。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryToolbar(
    query: String,
    onQueryChange: (String) -> Unit,
    sort: HistorySort,
    onSortChange: (HistorySort) -> Unit
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        SearchField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = stringResource(R.string.history_search_placeholder),
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(4.dp))

        Box {
            IconButton(onClick = { sortMenuExpanded = true }) {
                Icon(
                    Icons.AutoMirrored.Filled.Sort,
                    contentDescription = stringResource(R.string.history_sort),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            DropdownMenu(
                expanded = sortMenuExpanded,
                onDismissRequest = { sortMenuExpanded = false }
            ) {
                HistorySort.entries.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(stringResource(s.labelRes)) },
                        leadingIcon = {
                            if (s == sort) {
                                Icon(Icons.Default.Check, contentDescription = null)
                            }
                        },
                        onClick = {
                            sortMenuExpanded = false
                            onSortChange(s)
                        }
                    )
                }
            }
        }
    }
}
