package io.github.pia300.highlightreplay.engine

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import io.github.pia300.highlightreplay.data.MediaData
import java.io.File
import java.io.FileDescriptor
import java.io.IOException

private const val TAG = "Mp4Muxer"

/** 将音视频帧写入本地文件（Muxer 按路径创建）。 */
internal fun writeMp4(
    outputFile: File,
    videoDataList: List<MediaData>,
    audioDataList: List<MediaData>,
    videoFormat: MediaFormat,
    audioFormat: MediaFormat?
) {
    val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    muxToRelease(muxer) {
        muxAll(it, videoDataList, audioDataList, videoFormat, audioFormat)
    }
}

/** 将音视频帧写入文件描述符指向的输出（MediaStore 场景）。 */
internal fun writeMp4(
    fd: FileDescriptor,
    videoDataList: List<MediaData>,
    audioDataList: List<MediaData>,
    videoFormat: MediaFormat,
    audioFormat: MediaFormat?
) {
    val muxer = MediaMuxer(fd, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    muxToRelease(muxer) {
        muxAll(it, videoDataList, audioDataList, videoFormat, audioFormat)
    }
}

/** 执行写入、停止并释放 Muxer；释放失败不掩盖写入异常。 */
private fun muxToRelease(muxer: MediaMuxer, block: (MediaMuxer) -> Unit) {
    try {
        block(muxer)

        muxer.stop()
    } finally {
        try {
            muxer.release()
        } catch (_: Exception) {
        }
    }
}

/** 按 PTS 交错写入视频与音频样本，合成 MP4 文件。 */
private fun muxAll(
    muxer: MediaMuxer,
    videoDataList: List<MediaData>,
    audioDataList: List<MediaData>,
    videoFormat: MediaFormat,
    audioFormat: MediaFormat?
) {
    var videoCount = 0
    var audioCount = 0

    // start() 之后不能再注册轨道，故全部轨道须先注册。
    val videoTrackIndex = muxer.addTrack(videoFormat)
    Log.d(TAG, "Video track added: index=$videoTrackIndex")

    val audioTrackIndex = if (audioFormat != null && audioDataList.isNotEmpty()) {
        val index = muxer.addTrack(audioFormat)
        Log.d(TAG, "Audio track added: index=$index")
        index
    } else -1

    // 全部轨道注册完才能 start()，之后才可写样本。
    muxer.start()
    Log.d(TAG, "Muxer started")

    // 时间轴对齐：两轨各自按首样本 PTS 归零，音频再叠加相对视频首帧的墙钟差（ms→μs），保持采集时的相对起点。
    // 音频按视频时间窗口收集，故其首块不早于视频首帧，偏移恒 ≥ 0。
    val videoBasePtsUs = videoDataList.first().presentationTimeUs
    val audioFirstPtsUs = audioDataList.firstOrNull()?.presentationTimeUs ?: 0L
    val audioStartOffsetUs = if (audioDataList.isNotEmpty()) {
        (audioDataList.first().timeStamp - videoDataList.first().timeStamp) * 1000
    } else 0L

    // 诊断：两轨 PTS 与墙钟基准。视频 PTS 由 eglPresentationTimeANDROID 写入
    // （elapsedRealtimeNanos 绝对时刻），音频 PTS 由采样数换算（会话起点为 0），两者基准不同；
    // 此处记录实际值，便于核对成品时间轴。
    Log.d(
        TAG,
        "PTS align probe: videoBasePtsUs=$videoBasePtsUs, audioFirstPtsUs=$audioFirstPtsUs, " +
                "videoWallMs=${videoDataList.first().timeStamp}, " +
                "audioWallMs=${audioDataList.firstOrNull()?.timeStamp ?: -1L}, " +
                "wallDeltaUs=$audioStartOffsetUs, " +
                "audioLastPtsUs=${audioDataList.lastOrNull()?.presentationTimeUs ?: -1L}, " +
                "videoLastPtsUs=${videoDataList.last().presentationTimeUs}"
    )

    var lastVideoPts = Long.MIN_VALUE
    var lastAudioPts = Long.MIN_VALUE
    // 视频从自身 0 起算，音频叠加起始偏移；路径上均单调，clampPts 再兜底。
    fun rawPtsOf(data: MediaData, isVideo: Boolean): Long {
        return if (isVideo) {
            data.presentationTimeUs - videoBasePtsUs
        } else {
            (data.presentationTimeUs - audioFirstPtsUs) + audioStartOffsetUs
        }
    }

    // MediaMuxer 要求时间戳单调不减，此处强制严格递增。
    fun clampPts(raw: Long, last: Long): Long = if (raw > last) raw else last + 1

    var videoIndex = 0
    var audioIndex = 0
    // 复用单个 BufferInfo，避免逐帧分配；回调路径同步读取，不跨帧持有。
    val bufInfo = MediaCodec.BufferInfo()

    // 每次写 PTS 较小的一侧样本，直至两路耗尽。
    while (videoIndex < videoDataList.size ||
        (audioIndex < audioDataList.size && audioTrackIndex >= 0)
    ) {
        val videoData = if (videoIndex < videoDataList.size) videoDataList[videoIndex] else null
        val audioData = if (audioIndex < audioDataList.size) audioDataList[audioIndex] else null

        val videoRawPts = videoData?.let { rawPtsOf(it, isVideo = true) } ?: Long.MAX_VALUE
        val audioRawPts = audioData?.let { rawPtsOf(it, isVideo = false) } ?: Long.MAX_VALUE
        val videoPts = clampPts(videoRawPts, lastVideoPts)
        val audioPts = clampPts(audioRawPts, lastAudioPts)

        if (videoPts <= audioPts && videoData != null) {
            try {
                bufInfo.set(0, videoData.data.size, videoPts, videoData.flags)
                muxer.writeSampleData(videoTrackIndex, videoData.getByteBuffer(), bufInfo)
                videoCount++
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write video frame: ${e.message}")
                throw IOException("write video frame failed", e)
            }

            lastVideoPts = videoPts
            videoIndex++
        } else if (audioData != null && audioTrackIndex >= 0) {
            try {
                bufInfo.set(0, audioData.data.size, audioPts, audioData.flags)
                muxer.writeSampleData(audioTrackIndex, audioData.getByteBuffer(), bufInfo)
                audioCount++
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write audio frame: ${e.message}")
                throw IOException("write audio frame failed", e)
            }
            lastAudioPts = audioPts
            audioIndex++
        } else {

            // 可达情形：有音频帧但无音轨（audioFormat==null）；耗尽 audioIndex 防循环空转，有音轨时本分支不可达。
            audioIndex = audioDataList.size
        }
    }

    Log.d(TAG, "Write done: video=$videoCount frames, audio=$audioCount frames")
}
