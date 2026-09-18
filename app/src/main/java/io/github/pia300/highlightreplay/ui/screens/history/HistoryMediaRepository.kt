package io.github.pia300.highlightreplay.ui.screens.history

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import io.github.pia300.highlightreplay.R
import io.github.pia300.highlightreplay.data.LanguagePrefs
import io.github.pia300.highlightreplay.data.RecorderSettings
import io.github.pia300.highlightreplay.data.TagStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 重命名视频时强制使用的 MP4 文件扩展名。 */
const val MP4_EXTENSION = ".mp4"

/**
 * 历史视频的 MediaStore 与文件读写入口：查询、删除、重命名。
 *
 * 只做数据访问并返回结果，状态发布与用户提示由调用方负责。
 *
 * @param context 应用 Context；不持有界面 Context，可长于 Activity 生命周期。
 */
internal class HistoryMediaRepository(private val context: Context) {

    private companion object {
        private const val TAG = "HistoryMediaRepository"
    }

    /** 本应用视频目录的 MediaStore 查询子句（selection 与 args）。 */
    private fun ownVideoSelection(): Pair<String, Array<String>> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // 等值与带斜杠前缀二选一命中，兼容 RELATIVE_PATH 有无尾斜杠，排除前缀同名目录。
            val dir = "${android.os.Environment.DIRECTORY_MOVIES}/${RecorderSettings.SAVE_SUBDIR_NAME}"
            "${MediaStore.Video.Media.RELATIVE_PATH} = ? OR " +
                "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?" to arrayOf(dir, "$dir/%")
        } else {
            @Suppress("DEPRECATION")
            "${MediaStore.Video.Media.DATA} LIKE ?" to arrayOf(
                "%/${android.os.Environment.DIRECTORY_MOVIES}/${RecorderSettings.SAVE_SUBDIR_NAME}/%"
            )
        }

    /** 按资源 ID 读取本地化文本。 */
    private fun resolve(resId: Int, args: Array<Any?>): String =
        LanguagePrefs.string(context, resId, *args)

    /** 查询本应用视频目录下的全部视频，按添加时间倒序。 */
    @SuppressLint("Recycle")
    fun queryVideos(): List<VideoItem> {
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE
        )

        val (selection, args) = ownVideoSelection()
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        val items = mutableListOf<VideoItem>()
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection, selection, args, sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val durationMs = cursor.getLong(durationCol)
                val sizeBytes = cursor.getLong(sizeCol)
                val dateAdded = cursor.getLong(dateCol)
                items.add(
                    VideoItem(
                        uri = Uri.withAppendedPath(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id.toString()
                        ),
                        id = id,
                        displayName = cursor.getString(nameCol)
                            ?: LanguagePrefs.string(
                                context, R.string.history_fallback_name, id
                            ),
                        dateAdded = dateAdded,
                        durationMs = durationMs,
                        sizeBytes = sizeBytes,
                        sizeText = HistoryItemFormatter.sizeText(::resolve, sizeBytes),
                        durationText = HistoryItemFormatter.durationText(::resolve, durationMs),
                        dateText = HistoryItemFormatter.dateText(::resolve, dateAdded)
                    )
                )
            }
        }
        return items
    }

    /** 查询本应用视频目录中现存视频 ID（供系统删除后的差集清理）。 */
    // 游标经 use{} 自动关闭，故抑制 Recycle 警告。
    @SuppressLint("Recycle")
    fun remainingVideoIds(): Set<Long> {
        val (selection, args) = ownVideoSelection()
        val ids = mutableSetOf<Long>()
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Video.Media._ID), selection, args, null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            while (cursor.moveToNext()) ids.add(cursor.getLong(idCol))
        }
        return ids
    }

    /** 构建 Android 11+ 的系统删除确认请求；低版本返回 null。 */
    fun buildDeleteRequest(items: List<VideoItem>): android.app.PendingIntent? {

        // Android 11 以下无系统删除确认，返回 null 走旧版应用内删除逻辑。
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return try {
            MediaStore.createDeleteRequest(context.contentResolver, items.map { it.uri })
        } catch (e: Exception) {

            Log.e(TAG, "Failed to create system delete request", e)
            null
        }
    }

    /**
     * 在 IO 线程逐个删除视频并清理其标签。
     *
     * 单个条目失败不中断其余删除；返回删除失败的条目（受影响行数为 0 或抛异常）。
     */
    suspend fun delete(items: List<VideoItem>): List<VideoItem> = withContext(Dispatchers.IO) {
        val failed = mutableListOf<VideoItem>()
        items.forEach { item ->
            try {
                // delete 返回受影响行数，>0 才算删除成功并清理标签。
                val deleted = context.contentResolver.delete(item.uri, null, null)
                if (deleted > 0) {
                    TagStore.removeVideoTags(context, listOf(item.id))
                } else {
                    Log.e(TAG, "Delete returned 0 rows: ${item.uri}")
                    failed.add(item)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete video: ${item.uri}", e)
                failed.add(item)
            }
        }
        failed
    }

    /** 重命名视频，缺失扩展名时自动补全 .mp4，返回三态结果。 */
    suspend fun rename(item: VideoItem, newName: String): RenameResult =
        withContext(Dispatchers.IO) {
            val trimmed = newName.trim()

            val finalName =
                if (trimmed.endsWith(MP4_EXTENSION, ignoreCase = true)) trimmed
                else trimmed + MP4_EXTENSION

            // 最终名与原名相同时不执行 IO，返回 UNCHANGED。
            if (finalName == item.displayName) {
                return@withContext RenameResult.UNCHANGED
            }
            try {
                val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, finalName)
                    }
                    context.contentResolver.update(item.uri, values, null, null) > 0
                } else {

                    renameLegacyFile(item, finalName)
                }
                if (ok) RenameResult.SUCCESS else RenameResult.FAILED
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to rename video: ${item.uri}", e)
                RenameResult.FAILED
            }
        }

    /** 旧版重命名：读取物理路径、移动文件并更新 MediaStore，失败时回滚。 */
    // DATA 列已废弃但此处需读物理路径，故抑制警告。
    @Suppress("DEPRECATION")
    private fun renameLegacyFile(item: VideoItem, finalName: String): Boolean {
        val dataPath = context.contentResolver.query(
            item.uri, arrayOf(MediaStore.Video.Media.DATA), null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA))
            } else null
        } ?: return false
        val file = java.io.File(dataPath)
        val newFile = java.io.File(file.parentFile, finalName)

        if (!file.exists() || newFile.exists() || !file.renameTo(newFile)) return false
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, finalName)
                put(MediaStore.Video.Media.DATA, newFile.absolutePath)
            }
            val updated = context.contentResolver.update(item.uri, values, null, null) > 0
            if (!updated) {

                // 更新失败时把文件移回原名。
                newFile.renameTo(file)
            }
            updated
        } catch (e: Exception) {

            newFile.renameTo(file)
            false
        }
    }
}
