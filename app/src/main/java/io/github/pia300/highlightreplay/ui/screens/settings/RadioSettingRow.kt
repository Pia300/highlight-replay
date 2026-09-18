package io.github.pia300.highlightreplay.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import io.github.pia300.highlightreplay.ui.components.SettingGroupCard

/** 通用单选设置行：可选标题加一组带单选按钮的选项列表。 */
@Composable
internal fun RadioSettingRow(
    modifier: Modifier = Modifier,
    label: String = "",
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .selectableGroup()
    ) {
        if (label.isNotEmpty()) {
            Text(
                text = label,

                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = 4.dp,
                    end = 4.dp,
                    top = 4.dp,
                    bottom = 4.dp
                )
            )
        }
        SettingGroupCard {
            options.forEachIndexed { index, (entry, value) ->
                val isSelected = selected == value
                ListItem(
                    headlineContent = {
                        Text(
                            text = entry,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    trailingContent = {
                        RadioButton(
                            selected = isSelected,
                            // 点击已由整行 selectable 处理，清空单选项自身点击语义。
                            onClick = null,
                            modifier = Modifier.clearAndSetSemantics { }
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = { onSelect(value) }
                        )
                )
                if (index < options.lastIndex) {
                    SettingsDivider()
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
