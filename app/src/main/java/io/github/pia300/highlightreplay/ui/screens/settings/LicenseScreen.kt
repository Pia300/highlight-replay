package io.github.pia300.highlightreplay.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.pia300.highlightreplay.BuildConfig
import io.github.pia300.highlightreplay.R
import io.github.pia300.highlightreplay.ui.components.SectionHeader
import io.github.pia300.highlightreplay.ui.components.isLandscapeLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val APACHE_LICENSE_URL = "https://www.apache.org/licenses/LICENSE-2.0"
private const val GPL_LICENSE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"
private const val REMIX_ICON_URL = "https://remixicon.com"
private const val HIGHLIGHT_RECORDER_URL = "https://github.com/owen88ob/highlight-recorder"

/**
 * 随应用分发的第三方组件版本清单（展示用）。
 *
 * 版本值取自 [BuildConfig] 的 `DEP_*` 常量，其真源是 app/build.gradle 的版本声明；
 * 升级依赖后若未同步，ThirdPartyVersionsTest 会失败。
 */
internal val thirdPartyVersionList: List<Pair<String, String>> = listOf(
    "Compose UI" to BuildConfig.DEP_COMPOSE,
    "Material3" to BuildConfig.DEP_MATERIAL3,
    "Material Icons" to BuildConfig.DEP_ICONS,
    "Activity" to BuildConfig.DEP_ACTIVITY,
    "Lifecycle" to BuildConfig.DEP_LIFECYCLE,
    "Core KTX" to BuildConfig.DEP_COREKTX,
    "SavedState" to BuildConfig.DEP_SAVEDSTATE,
    "Tracing" to BuildConfig.DEP_TRACING
)

/** 把版本清单格式化为单行展示文本（与 THIRD_PARTY_NOTICES 的组件清单同源）。 */
internal fun formatThirdPartyVersions(versions: List<Pair<String, String>> = thirdPartyVersionList): String =
    versions.joinToString(" · ") { (name, version) -> "$name $version" }

/** 单个许可条目：组件名、许可名、版权/版本行与全文资源；点击行可展开离线全文。 */
private data class LicenseEntry(
    val name: String,
    val licenseName: String,
    val details: List<String>,
    val noteRes: Int? = null,
    val licenseUrl: String?,
    val licenseTextRes: Int
)

/** 许可证页：本项目自身许可 + 第三方组件清单；每行可展开查看完整许可证文本。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLandscape = isLandscapeLayout()

    // 资源取值须用 stringResource：LocalContext.getString 非配置感知，语言切换后不会刷新。
    val appName = stringResource(R.string.app_name)
    val gplLabel = stringResource(R.string.license_gpl_label)
    val androidxLabel = stringResource(R.string.settings_license_jetpack_androidx)
    val kotlinStdlibLabel = stringResource(R.string.settings_license_kotlin_stdlib)
    val coroutinesLabel = stringResource(R.string.settings_license_kotlinx_coroutines)
    val remixIconLabel = stringResource(R.string.settings_license_remix_icon)
    val highlightRecorderLabel = stringResource(R.string.settings_license_highlight_recorder)
    val apacheLabel = stringResource(R.string.settings_license_apache)

    val ownEntries = remember(appName, gplLabel) {
        listOf(
            LicenseEntry(
                name = appName,
                licenseName = gplLabel,
                details = listOf("Copyright (c) 2026 Pia300"),
                licenseUrl = GPL_LICENSE_URL,
                licenseTextRes = R.raw.gpl_license_3_0
            )
        )
    }
    val thirdPartyEntries = remember(
        androidxLabel, kotlinStdlibLabel, coroutinesLabel, remixIconLabel,
        highlightRecorderLabel, apacheLabel
    ) {
        listOf(
            LicenseEntry(
                name = androidxLabel,
                licenseName = apacheLabel,
                details = listOf(
                    "Copyright 2018 The Android Open Source Project",
                    formatThirdPartyVersions()
                ),
                licenseUrl = APACHE_LICENSE_URL,
                licenseTextRes = R.raw.apache_license_2_0
            ),
            LicenseEntry(
                name = kotlinStdlibLabel,
                licenseName = apacheLabel,
                details = listOf(
                    "Copyright JetBrains s.r.o. and Kotlin Programming Language contributors",
                    "kotlin-stdlib ${BuildConfig.DEP_KOTLIN}"
                ),
                licenseUrl = APACHE_LICENSE_URL,
                licenseTextRes = R.raw.apache_license_2_0
            ),
            LicenseEntry(
                name = coroutinesLabel,
                licenseName = apacheLabel,
                details = listOf(
                    "Copyright JetBrains s.r.o.",
                    "kotlinx-coroutines ${BuildConfig.DEP_COROUTINES}"
                ),
                licenseUrl = APACHE_LICENSE_URL,
                licenseTextRes = R.raw.apache_license_2_0
            ),
            LicenseEntry(
                name = remixIconLabel,
                licenseName = apacheLabel,
                details = listOf("Copyright (c) 2017-2026 Remix Design"),
                noteRes = R.string.license_modified_note,
                licenseUrl = REMIX_ICON_URL,
                licenseTextRes = R.raw.apache_license_2_0
            ),
            LicenseEntry(
                name = highlightRecorderLabel,
                licenseName = gplLabel,
                details = listOf(
                    "Copyright (c) owen88ob",
                    "Segment buffer design reference: data/SegmentRingBuffer.kt"
                ),
                noteRes = R.string.license_modified_note,
                licenseUrl = HIGHLIGHT_RECORDER_URL,
                licenseTextRes = R.raw.gpl_license_3_0
            )
        )
    }

    // 当前展开的条目名；null 表示全部收起。
    var expandedName by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxSize()) {

        // 仅横屏显示顶部栏；竖屏关闭入口来自宿主而非系统返回导航。
        if (isLandscape) {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings_license)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_license_back)
                        )
                    }
                },
                modifier = Modifier.height(48.dp),

                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item(key = "own-title") {
                SectionHeader(stringResource(R.string.settings_license_own_title))
            }
            ownEntries.forEach { entry ->
                item(key = "own-${entry.name}") {
                    LicenseRow(
                        entry = entry,
                        expanded = expandedName == entry.name,
                        onToggle = {
                            expandedName = if (expandedName == entry.name) null else entry.name
                        }
                    )
                }
            }

            item(key = "third-title") {
                SectionHeader(stringResource(R.string.settings_license_third_party_title))
            }
            thirdPartyEntries.forEach { entry ->
                item(key = "third-${entry.name}") {
                    LicenseRow(
                        entry = entry,
                        expanded = expandedName == entry.name,
                        onToggle = {
                            expandedName = if (expandedName == entry.name) null else entry.name
                        }
                    )
                }
            }
        }
    }
}

/** 单个许可条目行：点击展开/收起离线全文；展开区含修改声明与原文链接。 */
@Composable
private fun LicenseRow(
    entry: LicenseEntry,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            supportingContent = {
                Column {
                    Text(
                        text = entry.licenseName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    entry.details.forEach { detail ->
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            trailingContent = {
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp
                    else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
        )

        if (expanded) {
            ExpandedLicensePanel(entry)
        }

        HorizontalDivider()
    }
}

/** 展开区：附加声明（如有）、原文链接与可选择的完整许可证文本。 */
@Composable
private fun ExpandedLicensePanel(entry: LicenseEntry) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
    ) {
        entry.noteRes?.let { noteRes ->
            Text(
                text = stringResource(noteRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        entry.licenseUrl?.let { url ->
            OpenLicenseLinkRow(
                label = stringResource(R.string.settings_license_open_online),
                url = url
            )
        }
        // 全文内嵌在 raw 资源中，离线可读；可选择复制。
        val fullText = rememberRawText(entry.licenseTextRes)
        SelectionContainer {
            Text(
                text = fullText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 在系统浏览器中打开许可证原文的链接行。 */
@Composable
private fun OpenLicenseLinkRow(label: String, url: String) {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { openExternalLink(context, url) }
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.AutoMirrored.Outlined.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
    }
}

/** 异步读取 raw 资源文本（许可证全文），按资源 id 记忆；读取完成前返回空串。 */
@Composable
private fun rememberRawText(resId: Int): String {
    val context = LocalContext.current
    var text by remember(resId) { mutableStateOf("") }
    LaunchedEffect(resId) {
        text = withContext(Dispatchers.IO) {
            context.resources.openRawResource(resId)
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
        }
    }
    return text
}
