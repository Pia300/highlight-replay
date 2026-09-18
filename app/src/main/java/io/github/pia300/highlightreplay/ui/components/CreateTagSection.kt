package io.github.pia300.highlightreplay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.pia300.highlightreplay.R

/** 新建标签的颜色面板，首个颜色为默认色。 */
val TagPalette = listOf(
    0xFFBA1A1A, 0xFF8C4F0A, 0xFF5B6E00, 0xFF1A5FA8,
    0xFF0C7C59, 0xFF006874, 0xFF6750A4, 0xFF3E5A8F,
    0xFF6F5B40, 0xFF555F71, 0xFF7048B3, 0xFFB3261E
)

/** 新建标签区块：名称输入与颜色单选面板，创建成功后收起。 */
@Composable
internal fun CreateTagSection(
    onCreateTag: (name: String, color: Long) -> Boolean
) {
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newColor by remember { mutableLongStateOf(TagPalette[0]) }
    var duplicateError by remember { mutableStateOf(false) }

    if (!showCreate) {
        TextButton(onClick = { showCreate = true; duplicateError = false }) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.tag_new))
        }
    } else {
        OutlinedTextField(
            value = newName,
            onValueChange = { newName = it; duplicateError = false },
            label = { Text(stringResource(R.string.tag_name_label)) },
            singleLine = true,
            isError = duplicateError,
            supportingText = {
                if (duplicateError) Text(stringResource(R.string.tag_exists))
            }
        )
        Spacer(Modifier.height(8.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            // 单选组语义：读屏据此播报当前项在组内的位置与总数。
            modifier = Modifier.selectableGroup()
        ) {
            itemsIndexed(TagPalette, key = { _, color -> color }) { index, color ->
                val selected = color == newColor
                // 色板为纯视觉信息，以序号作为可辨识的无障碍名称。
                val colorLabel = stringResource(R.string.tag_color_option, index + 1)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = { newColor = color }
                        )
                        .semantics { contentDescription = colorLabel },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(color))
                            .border(
                                width = if (selected) 2.dp else 0.dp,
                                color = if (selected) MaterialTheme.colorScheme.onSurface
                                else Color.Transparent,
                                shape = CircleShape
                            )
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row {
            TextButton(
                onClick = {
                    val name = newName.trim()
                    if (onCreateTag(name, newColor)) {
                        newName = ""
                        showCreate = false
                    } else {
                        duplicateError = true
                    }
                },
                enabled = newName.isNotBlank()
            ) {
                Text(stringResource(R.string.tag_create))
            }
            TextButton(onClick = { showCreate = false }) {
                Text(stringResource(R.string.tag_cancel))
            }
        }
    }
}
