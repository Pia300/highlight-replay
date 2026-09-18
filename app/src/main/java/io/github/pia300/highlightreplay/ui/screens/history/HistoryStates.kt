package io.github.pia300.highlightreplay.ui.screens.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.pia300.highlightreplay.R

/**
 * 首次加载中（列表为空）时展示的加载指示器。
 */
@Composable
internal fun HistoryLoadingState(modifier: Modifier = Modifier) {
    CircularProgressIndicator(
        modifier = modifier,
        strokeCap = StrokeCap.Round
    )
}

/**
 * 加载失败且列表为空时展示的错误提示与重试按钮，[onRetry] 由调用方注入。
 */
@Composable
internal fun HistoryErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Outlined.Refresh,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(12.dp))

        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.history_retry))
        }
    }
}

/**
 * 列表为空时展示的空态：按查询词区分「搜索无结果」与「暂无录屏」，[onRefresh] 由调用方注入。
 */
@Composable
internal fun HistoryEmptyState(
    query: String,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Videocam,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (query.isNotBlank())
                stringResource(R.string.history_empty_search, query)
            else
                stringResource(R.string.history_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (query.isNotBlank())
                stringResource(R.string.history_empty_search_hint)
            else
                stringResource(R.string.history_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        TextButton(onClick = onRefresh) {
            Text(stringResource(R.string.history_empty_refresh))
        }
    }
}
