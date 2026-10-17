package io.github.pia300.highlightreplay.ui.screens.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.ExposedDropdownMenuAnchorType
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.pia300.highlightreplay.R
import io.github.pia300.highlightreplay.data.LanguagePrefs
import io.github.pia300.highlightreplay.ui.components.SettingIcons

/** 语言设置行：下拉菜单选择跟随系统或具体语言。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LanguageSettingRow(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
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
    val effectiveSelected = if (options.any { it.second == selected }) {
        selected
    } else {
        LanguagePrefs.LANG_SYSTEM
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth()
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = selectedLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            leadingContent = {
                Icon(
                    imageVector = SettingIcons.language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            },
            trailingContent = {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
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
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.exposedDropdownSize()
        ) {
            options.forEach { (label, value) ->
                val isSelected = value == effectiveSelected
                DropdownMenuItem(
                    text = {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                    leadingIcon = {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Spacer(Modifier.size(24.dp))
                        }
                    },
                    modifier = Modifier.semantics { this.selected = isSelected },
                    onClick = {
                        expanded = false
                        if (value != selected) onSelect(value)
                    }
                )
            }
        }
    }
}
