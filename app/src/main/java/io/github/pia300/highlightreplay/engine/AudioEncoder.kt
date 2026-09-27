package io.github.pia300.highlightreplay.engine

import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.os.SystemClock
import android.util.Log
import io.github.pia300.highlightreplay.data.RecorderSettings

/** 经 MediaProjection + AudioRecord 捕获系统音频并编码为 AAC；一次性实例，每会话新建。 */
class AudioEncoder(private val audioMonitorEnabled: Boolean) {

    companion object {
        private const val MIME_TYPE = "audio/mp4a-latm"
        private const val TAG = "AudioEncoder"

        // 采样率/声道数取自全局设置，与封装口径一致。
        private const val SAMPLE_RATE = RecorderSettings.AUDIO_SAMPLE_RATE
        // 256kbps AAC 码率，折中音质与体积。
        private const val BIT_RATE = 256000
        private const val CHANNEL_COUNT = RecorderSettings.AUDIO_CHANNEL_COUNT
        private const val DEQUEUE_TIMEOUT_US = 10_000L

        // 已废弃的 INFO_OUTPUT_BUFFERS_CHANGED 取值：个别旧 ROM 仍会返回，无输出槽可取。
        private const val INFO_OUTPUT_BUFFERS_CHANGED_LEGACY = -3

        // 每块 PCM 时长（ms，向下取整），仅用于把丢弃块数换算成大致时长（日志诊断用）。
        private const val BLOCK_DURATION_MS =
            RecorderSettings.AUDIO_BLOCK_SIZE * 1000 / RecorderSettings.AUDIO_BYTES_PER_SECOND
    }

    // @Volatile 保证标志与引用在编码线程与主线程之间可见。
    @Volatile
    private var encoder: MediaCodec? = null
    @Volatile
    private var audioRecord: AudioRecord? = null
    @Volatile
    private var isEncoding = false
    @Volatile
    private var stopped = false
    // release() 只执行一次。
    private var released = false

    @Volatile
    private var currentOutputFormat: MediaFormat? = null
    private var encodeThread: Thread? = null

    // 因输入缓冲不可用/占满而丢弃的块计数，仅日志诊断用。
    private var droppedInputBlocks = 0

    // 音频时间轴：按已送入的采样数换算 PTS。
    private val ptsClock = AudioPtsClock(SAMPLE_RATE, CHANNEL_COUNT)

    // 音频时间轴零点对应的单调时钟时刻（ms，elapsedRealtime）：会话首个数据块的内容起点。
    // 0 表示尚未建立（未开始录制或尚未读到数据）。
    @Volatile
    private var epochElapsedMs: Long = 0L

    @Volatile
    // 当前平滑音量（0..1），供 UI 显示电平。
    var currentAmplitude: Float = 0f
        private set

    /**
     * 编码帧的采集时刻（毫秒，单调时钟）：时间轴零点加该帧 PTS。
     *
     * PTS 由送入样本数换算且以会话首块为 0，故与零点相加即该帧内容的绝对时刻；
     * 零点未建立时退化为当前单调时刻。
     */
    fun frameTimeMs(presentationTimeUs: Long): Long {
        val epoch = epochElapsedMs
        return if (epoch == 0L) SystemClock.elapsedRealtime() else epoch + presentationTimeUs / 1000L
    }

    // 编码输出回调：每帧编码数据与 BufferInfo 交给封装器。
    var onOutputBufferAvailable: ((ByteArray, MediaCodec.BufferInfo) -> Unit)? = null

    /** 用 MediaProjection 创建编码器与系统音频捕获配置（Android 10+）。 */
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

    /** 开始录制并启动编码线程，循环读取并编码音频。 */
    fun start() {

        // 未 prepare 完成则跳过，防空指针。
        if (encoder == null || audioRecord == null) {
            Log.w(TAG, "start() skipped: audio encoder unavailable")
            isEncoding = false
            stopped = true
            return
        }
        // 清零会话内累计状态（采样、时间轴零点、电平、丢弃计数）。
        ptsClock.reset()
        epochElapsedMs = 0L
        currentAmplitude = 0f
        droppedInputBlocks = 0

        // 编码线程开始读取前，编码器与录音器须已启动。
        stopped = false
        encoder?.start()
        audioRecord?.startRecording()
        isEncoding = true
        encodeThread = Thread({ encodeLoop() }, "AudioEncoder-loop").apply { start() }
    }

    /** 置停止标志并等待编码线程退出（最多 2 秒）。 */
    fun stop() {
        if (stopped) return
        stopped = true
        isEncoding = false

        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }
        // 有界等待线程退出，避免与 release() 竞态。
        encodeThread?.join(2000)
    }

    /** 中断编码线程并释放 AudioRecord 与 MediaCodec。 */
    fun release() {
        if (released) return
        released = true
        isEncoding = false
        // AudioRecord.read 是阻塞读：须先 stop 解除阻塞，编码线程才能响应中断退出。
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

    /** 编码器当前输出格式。 */
    fun getOutputFormat(): MediaFormat? = currentOutputFormat

    /** 编码主循环：读 PCM → 计算电平 → 送编码器 → 取回编码帧，直至停止。 */
    private fun encodeLoop() {
        // 复用 BufferInfo；infoCopy 防回调数据被覆盖。
        val bufferInfo = MediaCodec.BufferInfo()
        val infoCopy = MediaCodec.BufferInfo()

        // PCM 为 16 位有符号小端，按固定块大小读取。
        val inputBuffer = ByteArray(RecorderSettings.AUDIO_BLOCK_SIZE)

        // 电平表：按块计算平滑音量（快升慢降，低于静音门限快速衰减）。
        val levelMeter = AudioLevelMeter()

        // 平均电平诊断仅在调试日志可输出时统计，正常构建零开销。
        val levelDiagEnabled = Log.isLoggable(TAG, Log.DEBUG)
        var captureLevelSum = 0f
        var captureLevelBlocks = 0

        // 连续读取错误计数：仅连续多次失败或致命错误码才退出。
        var consecutiveReadErrors = 0

        // 同时检查标志与中断状态，使 stop()/release() 均能退出循环。
        while (isEncoding && !Thread.currentThread().isInterrupted) {

            // 阻塞读取；-1 读取错误，0 暂无数据。
            val read = try {
                audioRecord?.read(inputBuffer, 0, inputBuffer.size) ?: -1
            } catch (e: Exception) {
                Log.e(TAG, "AudioRecord.read failed: ${e.message}", e)
                -1
            }
            if (read > 0) {
                consecutiveReadErrors = 0
                // 首个数据块确立时间轴零点：该块内容覆盖 [读取完成时刻 - 块时长, 读取完成时刻]，
                // 且其 PTS 为 0，故内容起点即零点。
                if (epochElapsedMs == 0L) {
                    epochElapsedMs = SystemClock.elapsedRealtime() -
                            read * 1000L / RecorderSettings.AUDIO_BYTES_PER_SECOND
                }

                if (audioMonitorEnabled) {
                    currentAmplitude = levelMeter.onSamples(inputBuffer, read)
                }
                if (levelDiagEnabled) {
                    captureLevelSum += currentAmplitude
                    captureLevelBlocks++
                    // 每 200 块输出一次平均电平（debug 级），排查“录到静音”。
                    if (captureLevelBlocks >= 200) {
                        Log.d(
                            TAG,
                            "internal-audio level diag: avg of last 200 blocks=" +
                                    "${captureLevelSum / captureLevelBlocks} (0=silent capture)"
                        )
                        captureLevelSum = 0f
                        captureLevelBlocks = 0
                    }
                }
                feedEncoder(inputBuffer, read)
            } else if (read < 0) {

                // ERROR_DEAD_OBJECT 或连续 3 次读取错误即退出；瞬时错误休眠后重试，成功读取清零计数。
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

                // 暂无数据时休眠，避免忙等占 CPU。
                try {
                    Thread.sleep(10)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
            // 每轮排空输出，保证编码帧及时送出。
            drainEncoder(bufferInfo, infoCopy)
        }

        // 循环结束后发 EOS 触发收尾输出；缓冲占满时最多重试 5 次。
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

        // 持续排空至收到 EOS 或 100 次上限，防无限循环。
        var drainCount = 0
        var eosSeen = false
        while (drainCount < 100 && !eosSeen) {
            drainCount++
            eosSeen = drainEncoder(bufferInfo, infoCopy)
        }

        Log.d(TAG, "Audio encoding thread ended")
    }

    /** 将一块 PCM 送入编码器输入缓冲并计算其 PTS；无论是否入队成功，时间轴都按真实经过的样本数前进。 */
    private fun feedEncoder(data: ByteArray, size: Int) {
        val enc = encoder ?: return
        try {
            val idx = enc.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
            if (idx >= 0) {
                var queued = false
                try {
                    val buf = enc.getInputBuffer(idx)
                    if (buf != null) {
                        buf.clear()
                        // 别假设输入槽一定放得下整块：槽容量由编码器决定，而本函数的 KDoc 承诺
                        // 「无论是否入队成功，时间轴都按真实经过的样本数前进」。若直接 put 整块，
                        // 容量不足会抛 BufferOverflowException 被外层 catch 吞掉——该块不入队、
                        // 不计入 droppedInputBlocks、时间轴也不前进，恰好违背该承诺。
                        // clear() 之后 remaining() 即槽容量（position=0、limit=capacity）。
                        val slotBytes = buf.remaining()
                        val blockStartPtsUs = ptsClock.currentPtsUs()
                        if (audioBlockFitsInputSlot(slotBytes, size)) {
                            buf.put(data, 0, size)
                            // PTS 取本块起始时刻：用累加前的采样数换算，随后再累加块内采样数。
                            ptsClock.advance(size)
                            enc.queueInputBuffer(idx, 0, size, blockStartPtsUs, 0)
                            queued = true
                        } else {
                            // 槽装不下整块：按"丢块"处置，与下方缓冲占满路径同一语义——
                            // 时间轴照真实样本数前进，样本丢弃并计数。
                            ptsClock.advance(size)
                            droppedInputBlocks++
                            Log.w(
                                TAG,
                                "audio input buffer smaller than block " +
                                        "($slotBytes < $size); dropped input block #$droppedInputBlocks " +
                                        "(~${droppedInputBlocks * BLOCK_DURATION_MS}ms)"
                            )
                        }
                    } else {

                        // getInputBuffer 为 null 仅见于编码器并发释放等异常；该块不写入任何样本，
                        // 输入槽由 finally 以零长度归还。时间轴仍按真实经过的样本数前进，否则丢块
                        // 会让音轨相对视频越来越早（渐进音画不同步）。
                        ptsClock.advance(size)
                        droppedInputBlocks++
                        Log.d(
                            TAG,
                            "audio input buffer unavailable (codec released?); dropped input block #$droppedInputBlocks"
                        )
                    }
                } finally {
                    // 已取出的输入槽无论是否写入都须以空数据归还，防异常路径泄漏。
                    if (!queued) {
                        try {
                            enc.queueInputBuffer(idx, 0, 0, 0L, 0)
                        } catch (_: Exception) {
                        }
                    }
                }
            } else {

                // 缓冲占满时丢弃该块；首次及每 100 块告警一次。
                // 同 getInputBuffer 失败路径：时间轴按真实经过的样本数前进，避免音画渐进不同步。
                ptsClock.advance(size)
                droppedInputBlocks++
                if (droppedInputBlocks == 1 || droppedInputBlocks % 100 == 0) {
                    Log.w(
                        TAG,
                        "audio input buffers full; dropped $droppedInputBlocks blocks " +
                                "(~${droppedInputBlocks * BLOCK_DURATION_MS}ms)"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "feedEncoder error: ${e.message}")
        }
    }

    /** 取出可用输出帧并回调；返回是否收到 EOS。 */
    private fun drainEncoder(
        bufferInfo: MediaCodec.BufferInfo,
        infoCopy: MediaCodec.BufferInfo
    ): Boolean {
        val enc = encoder ?: return false
        try {
            when (val idx = enc.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)) {
                // 格式变化：记录最新输出格式。
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
                            // 复制有效字节与修正后的 BufferInfo 交给回调。
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
                        // 检测 EOS；render=false 即只取数据不渲染。
                        eos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    } finally {
                        // 已取出的输出槽无论取数据是否成功都归还，防异常路径泄漏。
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
