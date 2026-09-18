package io.github.pia300.highlightreplay.engine

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log

/** 系统内录的 AudioRecord 构建。 */
internal object SystemAudioCapture {

    private const val TAG = "AudioEncoder"

    /**
     * 创建用于 MediaProjection 内录的 AudioRecord（16 位立体声）。
     *
     * @param mediaProjection 已授权的投影令牌。
     * @param sampleRate 采样率（Hz）。
     * @throws IllegalStateException 系统版本不支持或捕获配置无效时。
     */
    @SuppressLint("MissingPermission")
    fun create(mediaProjection: MediaProjection, sampleRate: Int): AudioRecord {
        // AudioPlaybackCapture 需 Android 10+；调用链已按版本门控，此处兜底显式失败，防调用方持有半初始化对象。
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Log.w(TAG, "System audio capture requires Android 10+")
            throw IllegalStateException("System audio capture requires Android 10+")
        }

        // 只捕获 MEDIA/GAME 用途音频，排除通话铃声等系统音。
        val captureConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .build()

        val audioFormat = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build()

        // 缓冲下限取系统要求与 4096 字节的较大者，避免底层缓冲区欠载。
        val minBufSize = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        // 基础缓冲翻倍，减少读取线程与消费端之间的抖动。
        val record = AudioRecord.Builder()
            .setAudioPlaybackCaptureConfig(captureConfig)
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(minBufSize * 2)
            .build()

        // 未初始化即捕获配置无效：释放已分配的录音器原生资源后按失败处理。
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            val state = record.state
            try {
                record.release()
            } catch (_: Exception) {
            }
            throw IllegalStateException("AudioRecord not initialized (state=$state)")
        }
        return record
    }
}
