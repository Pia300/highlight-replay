package io.github.pia300.highlightreplay.ui.screens.history

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import io.github.pia300.highlightreplay.data.THUMBNAIL_WAIT_POLL_MS
import io.github.pia300.highlightreplay.data.THUMBNAIL_WAIT_POLLS
import io.github.pia300.highlightreplay.data.inflightThumbnailLoads
import io.github.pia300.highlightreplay.data.loadVideoThumbnail
import io.github.pia300.highlightreplay.data.thumbnailCache
import io.github.pia300.highlightreplay.data.thumbnailKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 视频缩略图：缓存优先，未命中在 IO 线程加载并写回缓存，加载中或失败显示占位图标。
 */
@Composable
internal fun VideoThumbnail(
    item: VideoItem,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // 缩略图以内容为 key，内容变化后自动重新加载。
    val key = thumbnailKey(item.uri, item.sizeBytes, item.durationMs)
    // 初始化时同步读缓存，避免快速滚动时闪烁。
    var thumbnail by remember(key) {
        mutableStateOf(thumbnailCache.get(key))
    }
    LaunchedEffect(key) {

        if (thumbnail != null) return@LaunchedEffect
        // 同 key 已有加载在进行：先等其结果，避免重复解码。
        // 等待超时说明该加载异常缓慢，接手自行加载；若此刻在途登记已被他人取得，本行保持
        // 占位图——本 effect 就此结束，缓存随后被那位加载者写回也不会唤醒本行，只有该行
        // 离开组合再进入（重新取缓存初值）才会显示缩略图。
        if (!inflightThumbnailLoads.add(key)) {
            repeat(THUMBNAIL_WAIT_POLLS) {
                delay(THUMBNAIL_WAIT_POLL_MS)
                thumbnailCache.get(key)?.let {
                    thumbnail = it
                    return@LaunchedEffect
                }
            }
            if (!inflightThumbnailLoads.add(key)) return@LaunchedEffect
        }
        try {
            thumbnail = withContext(Dispatchers.IO) {
                try {
                    val loaded = loadVideoThumbnail(context, item.id, item.uri)
                    if (loaded != null) {
                        thumbnailCache.put(key, loaded)
                    }
                    loaded
                } catch (e: Exception) {

                    if (e is kotlinx.coroutines.CancellationException) throw e
                    null
                }
            }
        } finally {
            inflightThumbnailLoads.remove(key)
        }
    }

    val bitmap = thumbnail
    Surface(
        modifier = modifier.clip(MaterialTheme.shapes.small),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),

                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Videocam,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
