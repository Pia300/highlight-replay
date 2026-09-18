package io.github.pia300.highlightreplay.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 缩略图缓存的计价契约。
 *
 * `LruCache` 按 `sizeOf` 累加并在超预算时淘汰最旧条目；计价恒为 0 会使缓存失去上限。
 */
class VideoThumbnailCacheTest {

    @Test
    fun entryBytesEqualToBitmapByteCount() {
        assertEquals(50_176, thumbnailCacheEntryBytes(50_176))
    }

    @Test
    fun entryBytesIsPositiveForAnyPositiveBitmap() {
        listOf(1, 1_024, 50_176, 4 * 1024 * 1024).forEach { bytes ->
            assertTrue(
                "byteCount=$bytes 的计价必须为正，否则缓存不会被淘汰",
                thumbnailCacheEntryBytes(bytes) > 0
            )
        }
    }

    @Test
    fun cacheBudgetIsFiniteAndPositive() {
        // android.util.LruCache 在 JVM 单测中为空桩（maxSize() 返回 0），故只断言常量本身。
        assertEquals(4 * 1024 * 1024, THUMBNAIL_CACHE_MAX_BYTES)
        assertTrue(THUMBNAIL_CACHE_MAX_BYTES > 0)
    }
}
