package io.github.pia300.highlightreplay.engine

import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.os.Process
import android.os.SystemClock
import android.util.Log
import io.github.pia300.highlightreplay.data.RecorderSettings

class AudioEncoder(private val audioMonitorEnabled: Boolean) {

    companion object {
        private const val MIME_TYPE = "audio/mp4a-latm"
        private const val TAG = "AudioEncoder"

        private const val SAMPLE_RATE = RecorderSettings.AUDIO_SAMPLE_RATE
        private const val BIT_RATE = 256000
        private const val CHANNEL_COUNT = RecorderSettings.AUDIO_CHANNEL_COUNT
        private const val DEQUEUE_TIMEOUT_US = 10_000L

        private const val INFO_OUTPUT_BUFFERS_CHANGED_LEGACY = -3

        private const val FEED_RETRY_LIMIT = 1
    }

    @Volatile
    private var encoder: MediaCodec? = null
    @Volatile
    private var audioRecord: AudioRecord? = null
    @Volatile
    private var isEncoding = false
    @Volatile
    private var stopped = false
    private var released = false

    @Volatile
    private var currentOutputFormat: MediaFormat? = null
    private var encodeThread: Thread? = null

    private var droppedBytes = 0L
    private var firstBlockDelayMs = 0L

    private val ptsClock = AudioPtsClock(SAMPLE_RATE, CHANNEL_COUNT)

    @Volatile
    private var epochElapsedMs: Long = 0L

    @Volatile
    private var recordingStartedMs: Long = 0L

    @Volatile
    var currentAmplitude: Float = 0f
        private set

    fun frameTimeMs(presentationTimeUs: Long): Long {
        val epoch = epochElapsedMs
        return if (epoch == 0L) SystemClock.elapsedRealtime() else epoch + presentationTimeUs / 1000L
    }

    var onOutputBufferAvailable: ((ByteArray, MediaCodec.BufferInfo) -> Unit)? = null

    fun prepareWithMediaProjection(mediaProjection: MediaProjection) {
        try {
            encoder = MediaCodec.createEncoderByType(MIME_TYPE)
            val format =
                MediaFormat.createAudioFormat(MIME_TYPE, SAMPLE_RATE, CHANNEL_COUNT).apply {
                    setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
                    setInteger(
                        MediaFormat.KEY_AAC_PROFILE,
                        MediaCodecInfo.CodecProfileLevel.AACObjectLC
                    )
                }
            encoder?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

            audioRecord = SystemAudioCapture.create(mediaProjection, SAMPLE_RATE)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create audio encoder", e)

            try {
                encoder?.release()
            } catch (_: Exception) {
            }
            encoder = null
            try {
                audioRecord?.release()
            } catch (_: Exception) {
            }
            audioRecord = null
            throw RuntimeException("Failed to create audio encoder", e)
        }
    }

    fun start() {
        if (encoder == null || audioRecord == null) {
            Log.w(TAG, "start() skipped: audio encoder unavailable")
            isEncoding = false
            stopped = true
            return
        }
        ptsClock.reset()
        epochElapsedMs = 0L
        currentAmplitude = 0f
        droppedBytes = 0L
        firstBlockDelayMs = 0L

        stopped = false
        encoder?.start()
        recordingStartedMs = SystemClock.elapsedRealtime()
        audioRecord?.startRecording()
        isEncoding = true
        encodeThread = Thread({ encodeLoop() }, "AudioEncoder-loop").apply { start() }
    }

    fun stop() {
        if (stopped) return
        stopped = true
        isEncoding = false

        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }
        encodeThread?.join(2000)
    }

    fun release() {
        if (released) return
        released = true
        isEncoding = false
        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }
        encodeThread?.interrupt()

        try {
            encodeThread?.join(2000)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        encodeThread = null
        try {
            audioRecord?.release()
        } catch (_: Exception) {
        }
        audioRecord = null
        try {
            encoder?.stop()
        } catch (_: Exception) {
        }
        try {
            encoder?.release()
        } catch (_: Exception) {
        }
        encoder = null
    }

    fun getOutputFormat(): MediaFormat? = currentOutputFormat

    private fun encodeLoop() {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        } catch (_: Exception) {
        }

        val bufferInfo = MediaCodec.BufferInfo()
        val infoCopy = MediaCodec.BufferInfo()

        val inputBuffer = ByteArray(RecorderSettings.AUDIO_BLOCK_SIZE)

        val levelMeter = AudioLevelMeter()

        var consecutiveReadErrors = 0

        while (isEncoding && !Thread.currentThread().isInterrupted) {

            val read = try {
                audioRecord?.read(inputBuffer, 0, inputBuffer.size) ?: -1
            } catch (e: Exception) {
                Log.e(TAG, "AudioRecord.read failed: ${e.message}", e)
                -1
            }
            if (read > 0) {
                consecutiveReadErrors = 0
                if (epochElapsedMs == 0L) {
                    val readDoneMs = SystemClock.elapsedRealtime()
                    epochElapsedMs = readDoneMs - read * 1000L / RecorderSettings.AUDIO_BYTES_PER_SECOND
                    firstBlockDelayMs = readDoneMs - recordingStartedMs
                }

                if (audioMonitorEnabled) {
                    currentAmplitude = levelMeter.onSamples(inputBuffer, read)
                }
                feedEncoder(inputBuffer, read)
            } else if (read < 0) {
                consecutiveReadErrors++
                if (read == AudioRecord.ERROR_DEAD_OBJECT || consecutiveReadErrors >= 3) {
                    Log.e(
                        TAG,
                        "AudioRecord read error: $read (x$consecutiveReadErrors), stopping capture loop"
                    )
                    break
                }
                Log.w(TAG, "AudioRecord read error: $read (x$consecutiveReadErrors), retrying")
                try {
                    Thread.sleep(10)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            } else {
                try {
                    Thread.sleep(10)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
            drainEncoder(bufferInfo, infoCopy)
        }

        try {
            val enc = encoder
            if (enc != null) {
                var idx = -1
                for (attempt in 0 until 5) {
                    idx = enc.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                    if (idx >= 0) break
                    try {
                        Thread.sleep(10)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
                if (idx >= 0) {
                    enc.queueInputBuffer(
                        idx, 0, 0,
                        ptsClock.currentPtsUs(),
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to signal EOS: ${e.message}")
        }

        var drainCount = 0
        var eosSeen = false
        while (drainCount < 100 && !eosSeen) {
            drainCount++
            eosSeen = drainEncoder(bufferInfo, infoCopy)
        }

        Log.d(
            TAG,
            "audio encoding thread ended: queuedFrames=${ptsClock.framesQueued}, " +
                    "droppedFrames=${ptsClock.framesDropped}, droppedBytes=$droppedBytes, " +
                    "firstBlockDelayMs=$firstBlockDelayMs"
        )
    }

    private fun feedEncoder(data: ByteArray, size: Int) {
        if (size <= 0) return
        val blockStartPtsUs = ptsClock.currentPtsUs()
        var written = 0

        for (attempt in 0..FEED_RETRY_LIMIT) {
            written = feedBlock(data, size, blockStartPtsUs)
            if (written >= size) break
        }

        ptsClock.advanceQueued(written)
        if (written < size) {
            droppedBytes += size - written
            ptsClock.countDropped(size - written)
            Log.w(
                TAG,
                "audio input stalled; dropped ${size - written}B " +
                        "(total ${droppedBytes}B, ~${droppedBytes * 1000L / RecorderSettings.AUDIO_BYTES_PER_SECOND}ms)"
            )
        }
    }

    private fun feedBlock(data: ByteArray, size: Int, blockStartPtsUs: Long): Int {
        val enc = encoder ?: return 0
        var written = 0
        while (written < size) {
            val idx = try {
                enc.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
            } catch (e: Exception) {
                Log.e(TAG, "dequeueInputBuffer failed: ${e.message}")
                -1
            }
            if (idx < 0) break

            var queued = false
            try {
                val buf = enc.getInputBuffer(idx)
                if (buf != null) {
                    buf.clear()
                    val chunk = minOf(buf.remaining(), size - written)
                    if (chunk > 0) {
                        buf.put(data, written, chunk)
                        val ptsUs = blockStartPtsUs + written * 1_000_000L /
                                (CHANNEL_COUNT * SAMPLE_RATE)
                        enc.queueInputBuffer(idx, 0, chunk, ptsUs, 0)
                        queued = true
                        written += chunk
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "feedBlock error: ${e.message}")
            } finally {
                if (!queued) {
                    try {
                        enc.queueInputBuffer(idx, 0, 0, 0L, 0)
                    } catch (_: Exception) {
                    }
                }
            }
        }
        return written
    }

    private fun drainEncoder(
        bufferInfo: MediaCodec.BufferInfo,
        infoCopy: MediaCodec.BufferInfo
    ): Boolean {
        val enc = encoder ?: return false
        try {
            when (val idx = enc.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)) {
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    currentOutputFormat = enc.outputFormat
                }

                MediaCodec.INFO_TRY_AGAIN_LATER -> return false
                INFO_OUTPUT_BUFFERS_CHANGED_LEGACY -> return false
                in 0..Int.MAX_VALUE -> {
                    var eos: Boolean
                    try {
                        val buf = enc.getOutputBuffer(idx)
                        if (buf != null && bufferInfo.size > 0) {
                            val data = ByteArray(bufferInfo.size)
                            buf.position(bufferInfo.offset)
                            buf.limit(bufferInfo.offset + bufferInfo.size)
                            buf.get(data)
                            infoCopy.set(
                                0,
                                bufferInfo.size,
                                bufferInfo.presentationTimeUs,
                                bufferInfo.flags
                            )
                            onOutputBufferAvailable?.invoke(data, infoCopy)
                        }
                        eos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    } finally {
                        enc.releaseOutputBuffer(idx, false)
                    }
                    return eos
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "drainEncoder error: ${e.message}")
        }
        return false
    }
}
