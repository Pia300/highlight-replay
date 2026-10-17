package io.github.pia300.highlightreplay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** 单个选项的文字与可选图标。 */
@Immutable
data class SettingChoice(
    val value: String,
    val label: String,
    val icon: ImageVector? = null
)

/** 单个颜色选项；gradient 非空时以渐变绘制。 */
@Immutable
data class ThemeSwatch(
    val value: String,
    val label: String,
    val color: Color,
    val gradient: List<Color>? = null
)

/** 按选项数量选择分段按钮或 chip。 */
@Composable
fun SettingChoiceControl(
    options: List<SettingChoice>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val largeFont = LocalDensity.current.fontScale > 1.2f
    if (options.size == 2 && !largeFont) {
        SettingSegmentedChoice(options, selected, onSelect, modifier)
    } else {
        SettingChipChoice(options, selected, onSelect, modifier)
    }
}

/** 两选项横向分段按钮。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingSegmentedChoice(
    options: List<SettingChoice>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            val isSelected = option.value == selected
            SegmentedButton(
                selected = isSelected,
                onClick = { if (!isSelected) onSelect(option.value) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                icon = { SegmentedButtonDefaults.Icon(active = isSelected) },
                label = {
                    Text(
                        text = option.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}

/** 多选项 chip：4 项排成 2×2，其余流式换行。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingChipChoice(
    options: List<SettingChoice>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (options.size == 4) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.chunked(2).forEach { rowOptions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowOptions.forEach { option ->
                        SettingGridChip(
                            option = option,
                            selected = selected,
                            onSelect = onSelect,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    } else {
        FlowRow(
            modifier = modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                SettingFlowChip(
                    option = option,
                    selected = selected,
                    onSelect = onSelect
                )
            }
        }
    }
}

/** 流式 chip：宽度随内容。 */
@Composable
private fun SettingFlowChip(
    option: SettingChoice,
    selected: String,
    onSelect: (String) -> Unit
) {
    val isSelected = option.value == selected
    FilterChip(
        selected = isSelected,
        onClick = { if (!isSelected) onSelect(option.value) },
        label = {
            Text(
                text = option.label,
                style = MaterialTheme.typography.labelLarge
            )
        },
        leadingIcon = if (option.icon != null) {
            {
                Icon(
                    imageVector = option.icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            null
        },
        modifier = Modifier.semantics { role = Role.RadioButton }
    )
}

/** 网格 chip：等宽、文字居中。 */
@Composable
private fun SettingGridChip(
    option: SettingChoice,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isSelected = option.value == selected
    FilterChip(
        selected = isSelected,
        onClick = { if (!isSelected) onSelect(option.value) },
        label = {
            Text(
                text = option.label,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        modifier = modifier.semantics { role = Role.RadioButton }
    )
}

/** 主题色横向色板。 */
@Composable
fun SettingSwatchRow(
    options: List<ThemeSwatch>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { option ->
            val isSelected = option.value == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = { if (!isSelected) onSelect(option.value) }
                    )
                    .semantics(mergeDescendants = true) {
                        contentDescription = option.label
                    }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val gradient = option.gradient
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .then(
                                if (gradient != null) {
                                    Modifier.background(Brush.linearGradient(gradient))
                                } else {
                                    Modifier.background(option.color)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            val dynamic = option.gradient != null
                            val checkTint = when {
                                dynamic -> Color.White
                                option.color.luminance() > 0.5f -> Color(0xFF1A1C1E)
                                else -> Color.White
                            }
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = checkTint,
                                modifier = if (dynamic) {
                                    Modifier
                                        .size(18.dp)
                                        .background(Color.Black.copy(alpha = 0.22f), CircleShape)
                                        .padding(1.dp)
                                } else {
                                    Modifier.size(16.dp)
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
