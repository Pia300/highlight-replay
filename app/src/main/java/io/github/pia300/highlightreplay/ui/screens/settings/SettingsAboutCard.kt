package io.github.pia300.highlightreplay.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.pia300.highlightreplay.BuildConfig
import io.github.pia300.highlightreplay.R
import io.github.pia300.highlightreplay.ui.components.SettingGroupCard

private const val PROJECT_URL = "https://github.com/Pia300/highlight-replay"

/** "关于"卡片：应用名、版本，以及许可证与项目主页入口。 */
@Composable
internal fun AboutCard(
    modifier: Modifier = Modifier,
    onLicenseClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val versionName = BuildConfig.VERSION_NAME

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        SettingGroupCard {
            AboutInfoRow(
                label = stringResource(R.string.settings_about_app_name),
                value = stringResource(R.string.app_name)
            )
            SettingsDivider()
            AboutInfoRow(
                label = stringResource(R.string.settings_about_version),
                value = versionName
            )
            SettingsDivider()

            AboutLinkRow(
                label = stringResource(R.string.settings_license),
                icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                onClick = onLicenseClick
            )
            SettingsDivider()

            AboutLinkRow(
                label = stringResource(R.string.settings_about_project),
                icon = Icons.AutoMirrored.Outlined.OpenInNew,
                onClick = { openExternalLink(context, PROJECT_URL) }
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}

/** "关于"卡片链接行：左侧标题、右侧装饰图标，整行可点击。 */
@Composable
private fun AboutLinkRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        trailingContent = {
            Icon(
                imageVector = icon,
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
            .clickable(onClick = onClick)
    )
}

/** "关于"卡片信息行：左侧标签，右侧值。 */
@Composable
private fun AboutInfoRow(label: String, value: String) {
    ListItem(
        headlineContent = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        trailingContent = {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent
        ),
        modifier = Modifier.fillMaxWidth()
    )
}
