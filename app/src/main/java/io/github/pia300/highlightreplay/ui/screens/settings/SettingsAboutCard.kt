package io.github.pia300.highlightreplay.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.pia300.highlightreplay.BuildConfig
import io.github.pia300.highlightreplay.R
import io.github.pia300.highlightreplay.ui.components.SettingActionRow
import io.github.pia300.highlightreplay.ui.components.SettingIcons

private const val PROJECT_URL = "https://github.com/Pia300/highlight-replay"

/** "关于"卡片：应用名、版本、许可证与项目主页。 */
@Composable
internal fun AboutCard(
    modifier: Modifier = Modifier,
    onLicenseClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val versionName = BuildConfig.VERSION_NAME

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.weight(1f))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Text(
                    text = "v$versionName",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
        SettingsDivider(insetStart = 52.dp)
        SettingActionRow(
            icon = SettingIcons.license,
            title = stringResource(R.string.settings_license),
            onClick = onLicenseClick,
            trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight
        )
        SettingsDivider(insetStart = 52.dp)
        SettingActionRow(
            icon = SettingIcons.project,
            title = stringResource(R.string.settings_about_project),
            onClick = { openExternalLink(context, PROJECT_URL) },
            trailingIcon = Icons.AutoMirrored.Outlined.OpenInNew
        )
    }
}
