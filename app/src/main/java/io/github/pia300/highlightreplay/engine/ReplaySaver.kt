package io.github.pia300.highlightreplay.engine

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.media.MediaFormat
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.util.Log
import io.github.pia300.highlightreplay.R
import io.github.pia300.highlightreplay.data.CycleRingBuffer
import io.github.pia300.highlightreplay.data.LanguagePrefs
import io.github.pia300.highlightreplay.data.MediaData
import io.github.pia300.highlightreplay.data.RecorderSettings
import io.github.pia300.highlightreplay.data.SegmentRingBuffer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/** 将环形缓冲区的音视频数据封装成 MP4 文件并保存到设备。 */
class ReplaySaver(
    private val context: Context,
    private val settings: RecorderSettings,
    private val ringBuffer: SegmentRingBuffer,
    private val videoFormatProvider: () -> MediaFormat?,
    private val audioBuffer: CycleRingBuffer<MediaData>? = null,
    private val audioFormatProvider: (() -> MediaFormat?)? = null
) {

    companion object {
        private const val TAG = "ReplaySaver"
        private const val OUTPUT_EXTENSION = ".mp4"

        // 写入前的剩余空间余量比例（1/5）：容纳封装开销与并行写入，避免写到一半失败。
        private const val FREE_SPACE_HEADROOM_DIVISOR = 5
    }

    /** 保存回调：默认在后台保存线程回调；仅“已有保存在进行”的失败在调用线程同步回调。 */
    interface SaveCallback {
        fun onSaveStarted()
        fun onSaveCompleted()
        fun onSaveFailed(error: String)
    }

    // 原子标志，防并发重复保存。
    private val isSaving = AtomicBoolean(false)
    // 保存开始即快照编码器输出格式，防保存途中编码器被释放后取不到格式。
    @Volatile private var cachedVideoFormat: MediaFormat? = null
    @Volatile private var cachedAudioFormat: MediaFormat? = null

    /** 按当前语言偏好读取本地化字符串资源。 */
    private fun str(resId: Int, vararg args: Any?): String =
        LanguagePrefs.string(context, resId, *args)

    /** 在新线程触发保存；回调只归属本次保存，后续触发不会顶替本次收口。 */
    fun triggerSave(callback: SaveCallback) {
        // CAS 防重复保存；失败路径在调用线程同步回调。
        if (!isSaving.compareAndSet(false, true)) {
            callback.onSaveFailed(str(R.string.replay_saving_in_progress))
            return
        }
        // 保存开始即快照编码器输出格式，防保存途中编码器被释放后取不到格式。
        cachedVideoFormat = videoFormatProvider()
        cachedAudioFormat = audioFormatProvider?.invoke()

        // 保存涉及 IO 与封装，放独立线程执行。
        Thread({
            try {
                saveReplay(callback)
            } catch (e: Throwable) {

                Log.e(TAG, "Save failed", e)
                isSaving.set(false)
                callback.onSaveFailed(
                    str(
                        R.string.replay_save_failed,
                        e.message ?: str(R.string.error_recording_generic)
                    )
                )
            }
        }, "ReplaySaver").start()
    }

    /**
     * 完成收口：先复位保存标志，再以本次保存的回调触发完成。
     *
     * 不传产物路径：当前没有消费方——成功提示不带文件名（路径进 Toast 会把它拉长），
     * 也没有"打开刚存的文件"的入口。等真有消费方时再加参数，避免留一个无人读的形参。
     */
    private fun completeSave(callback: SaveCallback) {
        isSaving.set(false)
        callback.onSaveCompleted()
    }

    /** 失败收口：语义同 [completeSave]。 */
    private fun failSave(callback: SaveCallback, message: String) {
        isSaving.set(false)
        callback.onSaveFailed(message)
    }

    /**
     * 目标卷可分配字节数（含系统可清理的缓存数据），读取失败时返回 [Long.MAX_VALUE]（不阻断保存）。
     * 用 StorageManager.getAllocatableBytes 而非 File.usableSpace：后者不含可回收缓存，会误报空间不足。
     */
    private fun allocatableSpaceBytes(): Long = try {
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val uuid = storageManager.storageVolumes.firstOrNull { it.isPrimary }?.uuid
        if (uuid == null) Long.MAX_VALUE
        else storageManager.getAllocatableBytes(java.util.UUID.fromString(uuid))
    } catch (_: Exception) {
        Long.MAX_VALUE
    }

    /** 保存主流程：切取时间窗口、收集帧、封装并写入。 */
    private fun saveReplay(callback: SaveCallback) {
        callback.onSaveStarted()
        Log.d(TAG, "========== Saving replay ==========")

        // 快照缓冲，避免与录制线程的写入发生并发修改。
        // 分片缓冲按窗口取快照：起点落在覆盖窗口的首个分片边界（即关键帧）上。
        // 摊平为帧序列供后续封装使用；分片边界信息已由快照的起点选择体现。
        val videoList = ringBuffer.snapshot(settings.getReplayDurationMs())
            .flatMap { it.packets }
        val audioList = audioBuffer?.snapshot() ?: emptyList()

        val videoSize = videoList.size
        val videoStartTime =
            if (videoList.isEmpty()) Long.MAX_VALUE else videoList.minOf { it.timeStamp }
        val videoEndTime =
            if (videoList.isEmpty()) Long.MIN_VALUE else videoList.maxOf { it.timeStamp }

        Log.d(
            TAG,
            "Video buffer: $videoSize frames, ${ringBuffer.segmentCount} segments, " +
                    "${ringBuffer.bufferedDurationMs}ms, ${ringBuffer.bufferedBytes} bytes, " +
                    // 强制切段计数是本行唯一能回答「分片是否都以关键帧开头」的读数：
                    // 编码器始终不补发关键帧时该计数增长，产物仍可解码但切片边界不再对齐关键帧。
                    // 用户报「回放开头花屏」时需要它来区分「编码器不响应关键帧请求」与「切分选错起点」。
                    "forcedCuts=${ringBuffer.forcedCutCount}"
        )
        Log.d(TAG, "Video buffer time range: $videoStartTime - $videoEndTime")

        val audioSize = audioList.size

        Log.d(TAG, "Audio buffer size: $audioSize")

        if (videoSize == 0) {
            Log.e(TAG, "No data in buffer!")
            failSave(callback, str(R.string.replay_buffer_empty))
            return
        }

        // 时间窗口：以触发时刻为终点向前回溯回放时长，并夹在缓冲范围内。
        // 全程使用单调时钟（与帧时间戳同一口径）：系统对时/时区调整不会让窗口计算失效。
        val triggerTime = SystemClock.elapsedRealtime()
        val durationMs = settings.getReplayDurationMs()
        val targetStartTime = maxOf(triggerTime - durationMs, videoStartTime)
        val endTime = minOf(triggerTime, videoEndTime)

        Log.d(
            TAG,
            "save window: $targetStartTime - $endTime, duration: ${(endTime - targetStartTime) / 1000}s"
        )

        // 目标起点晚于最新数据，窗口内数据已全部过期。
        if (targetStartTime > videoEndTime) {
            Log.e(TAG, "Buffer data expired: latest=$videoEndTime, targetStart=$targetStartTime")
            failSave(callback, str(R.string.replay_data_expired))
            return
        }

        val selection = selectFramesInWindow(videoList, audioList, targetStartTime, endTime)
        if (selection == null) {
            failSave(callback, str(R.string.replay_no_keyframe))
            return
        }
        Log.d(TAG, "Start key frame found at: ${selection.startTimestamp}")

        val videoDataList = selection.videoFrames
        val audioDataList = selection.audioFrames

        Log.d(
            TAG,
            "collected ${videoDataList.size} video frames, range: " +
                    "${selection.startTimestamp} - ${selection.endTimestamp}"
        )
        if (audioDataList.isNotEmpty()) {
            Log.d(TAG, "Collected ${audioDataList.size} audio frames")
        }

        // 写入前预检剩余空间：不足则直接失败，避免写到一半留下损坏文件。
        val estimatedBytes = videoDataList.sumOf { it.data.size.toLong() } +
                audioDataList.sumOf { it.data.size.toLong() }
        val usable = allocatableSpaceBytes()
        if (usable != Long.MAX_VALUE && estimatedBytes > 0 &&
            usable < estimatedBytes + estimatedBytes / FREE_SPACE_HEADROOM_DIVISOR
        ) {
            Log.e(TAG, "Not enough free space: need~$estimatedBytes, usable=$usable")
            failSave(callback, str(R.string.replay_not_enough_space))
            return
        }

        // 无编码器输出格式则无法封装，中止。
        val videoFormat = cachedVideoFormat ?: videoFormatProvider() ?: run {
            Log.e(TAG, "Encoder output format unavailable; aborting save")
            failSave(callback, str(R.string.replay_encoder_format_unavailable))
            return
        }

        val audioFormat = if (audioDataList.isNotEmpty()) cachedAudioFormat else null
        // 有音频帧但格式未到（FORMAT_CHANGED 未收到）则无法注册音轨，降级为仅视频并显式告警。
        if (audioDataList.isNotEmpty() && cachedAudioFormat == null) {
            Log.w(
                TAG,
                "Audio frames present (${audioDataList.size}) but audio format unavailable; saving video-only"
            )
        }

        // Android 10+ 用 MediaStore，旧版写入公共目录文件。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveToMediaStore(videoDataList, audioDataList, videoFormat, audioFormat, callback)
        } else {
            saveToFile(videoDataList, audioDataList, videoFormat, audioFormat, callback)
        }
    }

    /** 生成“前缀_时间戳.mp4”文件名；毫秒级时间戳降低同名概率。 */
    private fun buildFileName(): String {
        // 日期字段纯数字；固定 Locale.US 防个别 locale 格式差异。
        val timeStamp =
            SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        return str(R.string.replay_file_prefix) + timeStamp + OUTPUT_EXTENSION
    }

    /** 经 MediaStore 将视频写入媒体库（Android 10+）。 */
    private fun saveToMediaStore(
        videoDataList: List<MediaData>,
        audioDataList: List<MediaData>,
        videoFormat: MediaFormat,
        audioFormat: MediaFormat?,
        callback: SaveCallback
    ) {
        val fileName = buildFileName()

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(
                MediaStore.Video.Media.RELATIVE_PATH,
                Environment.DIRECTORY_MOVIES + "/" + RecorderSettings.SAVE_SUBDIR_NAME
            )
            put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)

            // IS_PENDING=1：文件未写完，媒体扫描器暂不显示。
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }

        val uri =
            context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)

        if (uri == null) {
            failSave(callback, str(R.string.replay_create_media_failed))
            return
        }

        var pfd: android.os.ParcelFileDescriptor? = null
        var saved = false
        try {

            // 经文件描述符写入，不暴露真实路径。
            pfd = context.contentResolver.openFileDescriptor(uri, "w")
            if (pfd == null) {
                context.contentResolver.delete(uri, null, null)
                failSave(callback, str(R.string.replay_open_stream_failed))
                return
            }

            writeMp4(
                pfd.fileDescriptor,
                videoDataList,
                audioDataList,
                videoFormat,
                audioFormat
            )

            // 写完清除 PENDING，使文件在媒体库中立即可见。
            val done = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
            context.contentResolver.update(uri, done, null, null)
            saved = true
        } catch (e: Exception) {
            Log.e(TAG, "Save failed: ${e.message}")
            try {
                context.contentResolver.delete(uri, null, null)
            } catch (_: Exception) {
            }
        } finally {
            try {
                pfd?.close()
            } catch (_: Exception) {
            }
        }
        // 回调经收口 helper 在 try 外触发，回调异常不会删除已保存文件。
        if (saved) {
            completeSave(callback)
            Log.d(TAG, "Save completed: $uri")
        } else {
            failSave(callback, str(R.string.replay_save_failed, str(R.string.error_media_store_write)))
        }
    }

    /** 写入公共 Movies 目录的普通文件（Android 9 及以下）。 */
    private fun saveToFile(
        videoDataList: List<MediaData>,
        audioDataList: List<MediaData>,
        videoFormat: MediaFormat,
        audioFormat: MediaFormat?,
        callback: SaveCallback
    ) {
        val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        val directory = File(moviesDir, RecorderSettings.SAVE_SUBDIR_NAME)
        if (!directory.exists()) directory.mkdirs()

        val baseName = buildFileName()
        var outputFile = File(directory, baseName)
        // 文件已存在时追加递增后缀，避免覆盖。
        var suffix = 1
        while (outputFile.exists()) {
            val dot = baseName.lastIndexOf('.')
            val name = if (dot > 0) baseName.substring(0, dot) else baseName
            val ext = if (dot > 0) baseName.substring(dot) else ""
            outputFile = File(directory, "${name}_$suffix$ext")
            suffix++
        }
        Log.d(TAG, "Output file: ${outputFile.absolutePath}")

        try {
            writeMp4(outputFile, videoDataList, audioDataList, videoFormat, audioFormat)
        } catch (e: Exception) {

            Log.e(TAG, "Save failed: ${e.message}")
            try {
                outputFile.delete()
            } catch (_: Exception) {
            }
            failSave(
                callback,
                str(
                    R.string.replay_save_failed,
                    e.message ?: str(R.string.error_recording_generic)
                )
            )
            return
        }

        // 通知媒体扫描器索引新文件，使其立即可见。
        MediaScannerConnection.scanFile(
            context,
            arrayOf(outputFile.absolutePath),
            arrayOf("video/mp4"),
            null
        )

        completeSave(callback)
        Log.d(TAG, "Save completed: ${outputFile.absolutePath}")
    }

}
