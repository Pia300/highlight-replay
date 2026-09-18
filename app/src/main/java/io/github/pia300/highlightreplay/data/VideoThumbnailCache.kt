package io.github.pia300.highlightreplay.data

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache

/** 缩略图缓存字节预算（4MB）。 */
internal const val THUMBNAIL_CACHE_MAX_BYTES = 4 * 1024 * 1024

/**
 * 缩略图缓存条目计价（单位：字节）。
 *
 * 必须返回真实字节数：`LruCache` 按此值累加并在超预算时淘汰，返回 0 会使缓存失去上限。
 */
internal fun thumbnailCacheEntryBytes(byteCount: Int): Int = byteCount

/**
 * 全局缩略图内存缓存，容量上限 4MB；
 * 视频删除后由 HistoryScreen 整体清空缓存。
 */
internal val thumbnailCache = object : LruCache<String, android.graphics.Bitmap>(
    THUMBNAIL_CACHE_MAX_BYTES
) {

    override fun sizeOf(key: String, value: android.graphics.Bitmap): Int =
        thumbnailCacheEntryBytes(value.byteCount)
}

internal val inflightThumbnailLoads: MutableSet<String> =
    java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap())

// 等待同 key 在途加载的轮询参数：最多约 1.2s，超时后仍显示占位图。
internal const val THUMBNAIL_WAIT_POLL_MS = 60L
internal const val THUMBNAIL_WAIT_POLLS = 20

/** 缩略图缓存键：内容变化（URI/大小/时长）即失效。 */
internal fun thumbnailKey(uri: Uri, sizeBytes: Long, durationMs: Long): String =
    "$uri|$sizeBytes|$durationMs"

/**
 * 加载视频缩略图：Android 10+ 走 loadThumbnail，旧版查询 MediaStore 缩略图表。
 */
@Suppress("DEPRECATION")
internal fun loadVideoThumbnail(
    context: Context,
    id: Long,
    uri: Uri
): android.graphics.Bitmap? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        context.contentResolver.loadThumbnail(
            uri,
            android.util.Size(112, 112),
            null
        )
    } else {
        // 旧版查询 MediaStore 缩略图表；文件可能已删，捕获异常返回 null。
        try {
            MediaStore.Video.Thumbnails.getThumbnail(
                context.contentResolver,
                id,
                MediaStore.Video.Thumbnails.MINI_KIND,
                null
            )
        } catch (e: Exception) {
            null
        }
    }
}
