package io.github.pia300.highlightreplay.engine

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.Log
import android.view.Surface
import io.github.pia300.highlightreplay.data.EncoderPreference
import io.github.pia300.highlightreplay.data.RecorderSettings

/** 接收 Surface 输入并异步产出 H.264/HEVC 编码帧。 */
class VideoEncoder(private val settings: RecorderSettings) {

    companion object {
        private const val TAG = "VideoEncoder"
        private const val DEQUEUE_TIMEOUT_US = 10_000L

        // 已废弃的 INFO_OUTPUT_BUFFERS_CHANGED 取值：个别旧 ROM 仍会返回，无输出槽可取。
        private const val INFO_OUTPUT_BUFFERS_CHANGED_LEGACY = -3
    }

    /** 编码 MIME 类型，取自设置。 */
    private val mimeType: String get() = settings.getVideoMimeType()

    /** MediaCodec 编码器：调用线程创建/释放、排空线程读取，故 @Volatile。 */
    @Volatile
    private var encoder: MediaCodec? = null
    private var inputSurface: Surface? = null
    private var width: Int = 0
    private var height: Int = 0
    @Volatile
    private var isEncoding = false
    @Volatile
    private var stopped = false
    // 排空线程自增、调用线程读取，故 @Volatile。
    @Volatile
    private var frameCount = 0

    @Volatile
    private var currentOutputFormat: MediaFormat? = null
    private var drainThread: Thread? = null

    /** 编码输出回调，在排空线程上执行。 */
    var onOutputBufferAvailable: ((ByteArray, MediaCodec.BufferInfo) -> Unit)? = null

    /** 排空线程异常退出回调，在排空线程上执行：此后编码器不再产出，须由上层收尾会话。 */
    var onEncoderError: ((Exception) -> Unit)? = null

    /**
     * 计划使用的编码器名创建失败、改用系统代选时的回调，在调用线程执行。
     *
     * 该回退会同时改变**实际编码器**与**输出尺寸**，用户界面上却看不出任何差别；
     * 对照 ScreenRecorder 对 H.265 的处置（失败即报错中止，不静默降级），此处必须让用户知情。
     */
    var onEncoderFallback: (() -> Unit)? = null

    /** 按屏幕尺寸与录制设置创建并配置编码器（Surface 输入模式）。 */
    fun prepare(screenWidth: Int, screenHeight: Int) {

        // 重复 prepare 时先释放上一次的编码器与输入 Surface，避免 codec 泄漏。
        if (encoder != null || inputSurface != null) {
            release()
        }

        val (wantW, wantH) = VideoSizeCalculator.compute(
            screenWidth, screenHeight,
            settings.resolution, settings.resolutionModeEnum, settings.captureOrientation
        )

        // 编码器与尺寸的唯一决策点：同一次决策同时产出 codec 与尺寸，避免两者不一致。
        val preference = settings.encoderPreferenceEnum
        val plan = planEncoder(mimeType, wantW, wantH, preference)
        // 尺寸被能力钳制或按偏好选定了 codec：记录实际使用的配置，便于现场定位。
        if (plan.clamped || plan.codecName != null) {
            Log.d(
                TAG,
                "plan: pref=$preference, codec=${plan.codecName ?: "auto"}, " +
                        "size=${plan.width}x${plan.height}, hardware=${plan.hardware}, " +
                        "clamped=${plan.clamped} (requested ${wantW}x$wantH)"
            )
        }
        width = plan.width
        height = plan.height

        stopped = false
        // 重建 codec 后复位 released，使 release 之后可再次 prepare() 复用。
        released = false
        Log.d(
            TAG,
            "prepare: codec=${settings.codec}, mode=${settings.resolutionMode}, " +
                    "orientation=${settings.orientation}, output ${width}x${height}"
        )

        try {
            // 按 plan 选中的 codec 创建。指定名称创建失败时改用系统代选并重新规划尺寸；
            // 尺寸始终与最终实际使用的 codec 对齐——否则会用 A 的能力钳制出的尺寸去配置 B。
            var codec = plan.codecName
                ?.let { name -> runCatching { MediaCodec.createByCodecName(name) }.getOrNull() }
            if (codec == null && plan.codecName != null) {
                Log.w(TAG, "createByCodecName(${plan.codecName}) failed; falling back to system selection")
                // 回退同时改编码器与尺寸，用户界面上看不出差别：必须上报，不能只留一行日志。
                onEncoderFallback?.invoke()
                val fallback = planEncoder(mimeType, wantW, wantH, EncoderPreference.AUTO)
                width = fallback.width
                height = fallback.height
                codec = fallback.codecName
                    ?.let { name -> runCatching { MediaCodec.createByCodecName(name) }.getOrNull() }
                Log.d(
                    TAG,
                    "fallback plan: codec=${fallback.codecName ?: "auto"}, size=${width}x$height"
                )
            }
            if (codec == null) {
                codec = MediaCodec.createEncoderByType(mimeType)
                // 系统代选的实际 codec 决定可用尺寸，按它钳制请求尺寸。
                val actual = clampToCodec(codec.codecInfo, mimeType, wantW, wantH)
                width = actual.first
                height = actual.second
            }
            encoder = codec
            val format = MediaFormat.createVideoFormat(mimeType, width, height).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                )
                setInteger(MediaFormat.KEY_BIT_RATE, settings.getVideoBitRateBps())
                setInteger(MediaFormat.KEY_FRAME_RATE, settings.frameRate)
                // 声明为 best-effort（非实时）任务：让编码器调度给前台游戏让路，降低游戏掉帧。
                // 软约束，部分编码器忽略；代价是编码侧可能丢帧，对回录可接受。
                setInteger(MediaFormat.KEY_PRIORITY, 1)

                // Android 10+ 限制送入编码器的最大帧率，降低功耗。
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setInteger(MediaFormat.KEY_MAX_FPS_TO_ENCODER, settings.frameRate)
                }

                // 1 秒关键帧间隔，便于回放快速跳转。
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                // 显式禁用 B 帧：缓冲切帧与封装假定输出序即解码序、PTS 单调。
                // 该键自 API 29 起可用；更早版本无需设置——其文档默认值即为 0（不允许 B 帧）。
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0)
                }
            }
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = codec.createInputSurface()

            // Android 11+ 固定输入帧率与编码帧率一致，减少画面抖动。
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    inputSurface?.setFrameRate(
                        settings.frameRate.toFloat(),
                        Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "setFrameRate unsupported; FrameRateLimiter covers it: ${e.message}")
                }
            }
        } catch (e: Exception) {
            // 创建/配置调用可能抛 RuntimeException 子类或 IOException（createEncoderByType 的失败路径），
            // 统一释放已创建的 codec 后包装重抛。
            Log.e(TAG, "Failed to create/configure video encoder", e)
            try {
                encoder?.release()
            } catch (_: Exception) {
            }
            encoder = null
            throw RuntimeException("Failed to create video encoder", e)
        }
    }

    /** 启动编码器并开启后台排空线程取回编码输出。 */
    fun start() {
        stopped = false
        encoder?.start()
        isEncoding = true
        frameCount = 0
        // 排空线程设为近音频的高优先级，降低编码延迟。
        drainThread = Thread({

            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            } catch (_: Exception) {
            }
            drainEncoder()
        }, "VideoEncoder-drain").apply { start() }
    }

    /** 通知输入结束并等待排空线程取出剩余帧。 */
    fun stop() {
        if (stopped) return
        stopped = true
        Log.d(TAG, "stop() called, frames: $frameCount")
        // signalEndOfInputStream：刷出剩余数据并最终发 EOS。
        try {
            encoder?.signalEndOfInputStream()
        } catch (e: Exception) {
            Log.e(TAG, "signalEndOfInputStream error: ${e.message}")
        }

        // 有界等待排空线程退出，避免长时间阻塞调用线程。
        drainThread?.join(2000)
        isEncoding = false
    }

    private var released = false

    /** 释放编码器、输入 Surface 与排空线程；幂等。释放后须再次 prepare() 才能复用。 */
    fun release() {
        if (released) return
        released = true
        isEncoding = false
        // 释放 codec 前须先中断并 join 排空线程（≤2s），防其访问正在释放的对象。
        drainThread?.interrupt()
        try {
            drainThread?.join(2000)
        } catch (e: InterruptedException) {
            // 恢复中断标志，避免吞掉中断状态。
            Thread.currentThread().interrupt()
        }
        drainThread = null

        try {
            inputSurface?.release()
        } catch (_: Exception) {
        }
        inputSurface = null
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

    fun getInputSurface(): Surface? = inputSurface
    fun getWidth(): Int = width
    fun getHeight(): Int = height

    /**
     * 请求下一帧编码为关键帧。
     *
     * 部分编码器不严格遵循 [MediaFormat.KEY_I_FRAME_INTERVAL]，分片缓冲据此主动干预。
     * 参数不受支持时编码器会抛异常：此处吞掉并记日志，由缓冲的硬上限兜底。
     */
    fun requestKeyFrame() {
        if (stopped) return
        val enc = encoder ?: return
        try {
            enc.setParameters(Bundle().apply {
                putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
            })
        } catch (e: Exception) {
            Log.w(TAG, "requestKeyFrame failed: ${e.message}")
        }
    }

    /** 已产出的编码帧数，用于判定排空线程是否仍在推进。 */
    fun getFrameCount(): Int = frameCount

    fun getOutputFormat(): MediaFormat? = currentOutputFormat

    /** 在后台线程循环取编码输出，回调并统计帧数。 */
    private fun drainEncoder() {
        val bufferInfo = MediaCodec.BufferInfo()
        val infoCopy = MediaCodec.BufferInfo()

        while (isEncoding && !Thread.currentThread().isInterrupted) {
            val enc = encoder ?: break
            try {
                when (val idx = enc.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)) {
                    // 保存输出格式变化（SPS/PPS 就绪），供 muxer 使用。
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        currentOutputFormat = enc.outputFormat
                    }

                    MediaCodec.INFO_TRY_AGAIN_LATER -> continue
                    INFO_OUTPUT_BUFFERS_CHANGED_LEGACY -> continue
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
                                frameCount++
                            }
                            eos =
                                (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        } finally {
                            // 已取出的输出槽无论取数据是否成功都归还，防异常路径泄漏。
                            enc.releaseOutputBuffer(idx, false)
                        }
                        if (eos) break
                    }
                }
            } catch (e: IllegalStateException) {
                Log.e(TAG, "drainEncoder state error: ${e.message}")
                onEncoderError?.invoke(e)
                break
            } catch (e: Throwable) {

                Log.e(TAG, "drainEncoder error: ${e.message}")
                onEncoderError?.invoke(e as? Exception ?: Exception(e))
                break
            }
        }
        Log.d(TAG, "drainEncoder ended, total frames: $frameCount")
    }
}
