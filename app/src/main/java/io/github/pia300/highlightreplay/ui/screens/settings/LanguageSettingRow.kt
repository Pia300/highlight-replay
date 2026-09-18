package io.github.pia300.highlightreplay.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import io.github.pia300.highlightreplay.R
import io.github.pia300.highlightreplay.data.LanguagePrefs

/** 语言设置行：下拉菜单提供跟随系统、中英与日韩西葡印越泰俄文选项。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LanguageSettingRow(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // 选项列表含 12 次字符串解析，仅随所选语言变化重建。
    val options = remember(selected) {
        fun str(resId: Int): String = LanguagePrefs.string(context, resId)
        listOf(
            str(R.string.settings_language_system) to LanguagePrefs.LANG_SYSTEM,
            str(R.string.settings_language_zh_cn) to LanguagePrefs.LANG_ZH_CN,
            str(R.string.settings_language_zh_tw) to LanguagePrefs.LANG_ZH_TW,
            str(R.string.settings_language_en) to LanguagePrefs.LANG_EN,
            str(R.string.settings_language_ja) to LanguagePrefs.LANG_JA,
            str(R.string.settings_language_ko) to LanguagePrefs.LANG_KO,
            str(R.string.settings_language_es) to LanguagePrefs.LANG_ES,
            str(R.string.settings_language_pt) to LanguagePrefs.LANG_PT,
            str(R.string.settings_language_id) to LanguagePrefs.LANG_ID,
            str(R.string.settings_language_vi) to LanguagePrefs.LANG_VI,
            str(R.string.settings_language_th) to LanguagePrefs.LANG_TH,
            str(R.string.settings_language_ru) to LanguagePrefs.LANG_RU
        )
    }
    val selectedLabel = options.firstOrNull { it.second == selected }?.first
        ?: stringResource(R.string.settings_language_system)

    // 无效的已存取值按"跟随系统"处理，保证菜单始终有选中项。
    val effectiveSelected =
        if (options.any { it.second == selected }) selected else LanguagePrefs.LANG_SYSTEM

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {

                ListItem(
                    headlineContent = {
                        Text(
                            text = selectedLabel,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    trailingContent = {
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },

                    modifier = Modifier.exposedDropdownSize()
                ) {
                    options.forEach { (label, value) ->
                        val isSelected = value == effectiveSelected
                        DropdownMenuItem(
                            text = { Text(label, style = MaterialTheme.typography.bodyLarge) },
                            leadingIcon = {

                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            },
                            // 选中态写入语义树，TalkBack 可获知当前语言。
                            modifier = Modifier.semantics { this.selected = isSelected },
                            onClick = {
                                // 收起菜单；取值变化时才通知外层，避免无谓持久化。
                                expanded = false
                                if (value != selected) onSelect(value)
                            }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
