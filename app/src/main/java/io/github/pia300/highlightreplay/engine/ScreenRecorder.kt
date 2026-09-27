package io.github.pia300.highlightreplay.engine

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Display
import io.github.pia300.highlightreplay.R
import io.github.pia300.highlightreplay.data.CaptureOrientation
import io.github.pia300.highlightreplay.data.CycleRingBuffer
import io.github.pia300.highlightreplay.data.LanguagePrefs
import io.github.pia300.highlightreplay.data.MediaData
import io.github.pia300.highlightreplay.data.RecorderSettings
import io.github.pia300.highlightreplay.data.SegmentRingBuffer
import io.github.pia300.highlightreplay.data.VideoCodec
import java.util.concurrent.atomic.AtomicBoolean

/** 采集屏幕（含音频）并写入环形缓冲区。 */
class ScreenRecorder(
    private val context: Context,
    private val settings: RecorderSettings,
    private val mediaProjection: MediaProjection
) {
    /** 日志标签与常量。 */
    companion object {
        private const val TAG = "ScreenRecorder"

        // 旋转适配状态计算节流间隔（单调时钟计时）。
        private const val ADAPT_CHECK_INTERVAL_MS = 500L
    }

    // 编码器引用跨线程读写，故 @Volatile。
    @Volatile
    private var videoEncoder: VideoEncoder? = null

    @Volatile
    private var audioEncoder: AudioEncoder? = null

    // 主线程在旋转适配中调整虚拟显示尺寸，故 @Volatile。
    @Volatile
    private var virtualDisplay: VirtualDisplay? = null

    // 排空线程与主线程都会读取限帧层的渲染时刻，故 @Volatile。
    @Volatile
    private var frameRateLimiter: FrameRateLimiter? = null

    /**
     * 是否走直连编码器（限帧层初始化失败的回退路径）。
     *
     * 直连时编码器只在屏幕内容变化时收到帧，静止画面下没有输出，「画面活跃」无法可靠判定。
     */
    @Volatile
    private var directConnection = false

    // 系统停止投影（如撤销授权）时置位，供其它线程读取。
    @Volatile
    private var projectionStopped = false
    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            projectionStopped = true

            // 撤销授权须同时结束会话并告知原因，故同时触发两个回调；资源停止由外部引擎任务统一执行，本类只负责通知。
            handler.post {
                onProjectionStopped?.invoke()
                onError?.invoke(
                    Exception(
                        LanguagePrefs.string(
                            context,
                            R.string.replay_error_projection_stopped
                        )
                    )
                )
            }
        }
    }

    private lateinit var ringBuffer: SegmentRingBuffer
    private var audioBuffer: CycleRingBuffer<MediaData>? = null
    private val isRecording = AtomicBoolean(false)

    // 保证 stop() 只生效一次。
    private val isStopped = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())
    // 最近一帧视频入缓冲的单调时钟时刻（ms，elapsedRealtime），用于视频输入活性判定与回放窗口切分。
    @Volatile
    var lastFrameTimeMs: Long = 0
        private set
    // 最近一段音频入缓冲的单调时钟时刻（ms）。
    @Volatile
    var lastAudioFrameTimeMs: Long = 0
        private set
    // 最近一次真实音频电平出现的单调时钟时刻（ms）；静音不更新，供“有声音输入”判定。
    @Volatile
    private var lastAudioPeakTimeMs: Long = 0L

    // AUTO 旋转模式基准：初始化时屏幕是否为横屏。
    private var initialScreenLandscape = false

    // 旋转适配状态缓存，500ms 节流期内复用。
    @Volatile
    private var cachedAdaptState: AdaptState? = null
    // 上次计算适配状态时的单调时钟读数。
    @Volatile
    private var lastAdaptCheckMs = 0L

    private var captureDensityDpi = 0

    /** 屏幕方向与尺寸查询。 */
    private val displayGeometry = DisplayGeometry(context)

    // 供限帧层查询旋转适配状态：旋转关闭返回 null，开启时按固定间隔节流计算（单调时钟，不受墙钟调整影响）。
    private val adaptStateProvider: () -> AdaptState? = {
        if (!settings.contentRotationEnabled) {
            null
        } else {
            val now = SystemClock.elapsedRealtime()
            if (now - lastAdaptCheckMs >= ADAPT_CHECK_INTERVAL_MS) {
                lastAdaptCheckMs = now
                cachedAdaptState = computeAdaptState()
            }
            cachedAdaptState
        }
    }

    /** 上次打印适配诊断时的状态摘要（方向/旋转/尺寸），仅在变化时打印，避免 500ms 一次刷屏。 */
    @Volatile
    private var lastAdaptProbe: String? = null

    /** 按当前屏幕尺寸与采集方向计算旋转适配状态（0 或 90 度）。 */
    private fun computeAdaptState(): AdaptState {
        val metrics = displayGeometry.realDisplayMetrics()
        val screenLandscape = metrics.widthPixels > metrics.heightPixels
        // 诊断：对比 Service 的 configuration、display.rotation 与计算出的宽高，确认 realDisplayMetrics
        // 的方向校正与真实方向一致（若 Service 的 Resources 不随旋转更新，orientation 会滞后导致宽高反向交换）。
        // 仅在状态变化时打印，避免 500ms 一次刷屏。
        val configOrientation = context.resources.configuration.orientation
        val displayRotation = try {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager)
                .getDisplay(Display.DEFAULT_DISPLAY)?.rotation
        } catch (_: Exception) {
            null
        }
        val probe = "$configOrientation/$displayRotation/${metrics.widthPixels}x${metrics.heightPixels}"
        if (probe != lastAdaptProbe) {
            lastAdaptProbe = probe
            Log.d(
                TAG,
                "adapt probe: configOrientation=$configOrientation, displayRotation=$displayRotation, " +
                        "metrics=${metrics.widthPixels}x${metrics.heightPixels}, " +
                        "screenLandscape=$screenLandscape, initialLandscape=$initialScreenLandscape"
            )
        }
        // 屏幕方向与采集方向不匹配时旋转 90 度。
        val rotation = when (settings.captureOrientation) {
            CaptureOrientation.LANDSCAPE -> if (screenLandscape) 0 else 90
            CaptureOrientation.PORTRAIT -> if (screenLandscape) 90 else 0

            CaptureOrientation.AUTO -> if (screenLandscape == initialScreenLandscape) 0 else 90
        }
        return AdaptState(rotation, metrics.widthPixels, metrics.heightPixels)
    }

    /** 旋转适配时在主线程调整虚拟显示分辨率。 */
    private fun resizeVirtualDisplayForAdapt(w: Int, h: Int) {
        handler.post {
            try {
                val vd = virtualDisplay ?: return@post
                vd.resize(w, h, captureDensityDpi)
                Log.d(TAG, "Virtual display resized: ${w}x$h (content rotation follows orientation)")
            } catch (e: Exception) {
                // 调整失败不致命，维持原尺寸继续录制。
                Log.w(TAG, "VirtualDisplay.resize failed (keeping original size): ${e.message}")
            }
        }
    }

    // 录制开始回调，主线程触发。
    var onRecordingStarted: (() -> Unit)? = null
    // 录制停止回调，主线程触发。
    var onRecordingStopped: (() -> Unit)? = null
    // 错误回调，携带异常，主线程触发。
    var onError: ((Exception) -> Unit)? = null

    // 投影被系统停止（撤销授权等）回调，主线程触发。
    var onProjectionStopped: (() -> Unit)? = null

    /** 初始化编码器、限帧层、缓冲与虚拟显示；失败时释放已创建资源并抛出异常。 */    fun prepare() {

        // 重复 prepare 时先释放上次遗留资源，避免泄漏。
        if (videoEncoder != null || audioEncoder != null || virtualDisplay != null || frameRateLimiter != null) {
            release()
        }

        try {
            // 先注销旧回调，防重复注册抛异常。
            mediaProjection.unregisterCallback(projectionCallback)
        } catch (_: Exception) {
        }
        mediaProjection.registerCallback(projectionCallback, handler)
        projectionStopped = false

        try {
            // 系统音频捕获需 Android 10+。
            if (settings.hasAudio() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

                try {
                    audioEncoder = AudioEncoder(audioMonitorEnabled = settings.audioMonitorEnabled).apply {
                        prepareWithMediaProjection(mediaProjection)
                        onOutputBufferAvailable = { data, info ->

                            // 仅录制时入缓冲，跳过 CODEC_CONFIG 配置包。
                            if (isRecording.get() && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {

                                // 帧时间戳取该帧内容自身的采集时刻（单调时钟毫秒）：音频 PTS 由送入样本数换算
                                // 且以会话首块为 0，与时间轴零点相加即绝对时刻。
                                val audioTimeMs = audioEncoder?.frameTimeMs(info.presentationTimeUs)
                                    ?: SystemClock.elapsedRealtime()
                                lastAudioFrameTimeMs = audioTimeMs
                                // 内录静音时仍持续产出零电平 PCM，故只有非零电平才算有声音输入。
                                val amplitude = audioEncoder?.currentAmplitude ?: 0f
                                if (amplitude > 0f) {
                                    lastAudioPeakTimeMs = audioTimeMs
                                }
                                audioBuffer?.put(
                                    MediaData(
                                        data,
                                        info.presentationTimeUs, info.flags,
                                        lastAudioFrameTimeMs
                                    )
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    // 音频初始化失败不中断录制，降级为仅视频。
                    Log.w(TAG, "Audio init failed; continuing video-only", e)
                    audioEncoder = null
                }
            }

            val metrics = displayGeometry.realDisplayMetrics()

            val encoder = VideoEncoder(settings)
            try {
                encoder.prepare(metrics.widthPixels, metrics.heightPixels)
            } catch (e: Exception) {
                // H.265 仅在设备支持时可选，prepare 失败属真实配置错误：不静默降级，报错中止。
                if (settings.codecEnum == VideoCodec.H265) {
                    throw Exception(
                        LanguagePrefs.string(context, R.string.error_codec_h265_start_failed),
                        e
                    )
                }
                throw e
            }
            videoEncoder = encoder
            videoEncoder?.onEncoderError = { e ->
                // 排空线程已退出，编码器不再产出：转成会话错误由上层收尾。
                handler.post {
                    onError?.invoke(
                        Exception(
                            LanguagePrefs.string(
                                context,
                                R.string.error_video_encoder_stalled
                            ),
                            e
                        )
                    )
                }
            }
            videoEncoder?.onEncoderFallback = {
                // 编码器与输出尺寸都被改写，用户界面上看不出差别：必须经 onError 上报。
                // 与 H.265 的处理（ScreenRecorder.kt:254 附近的报错中止）同一诉求：不静默降级。
                handler.post {
                    onError?.invoke(
                        Exception(LanguagePrefs.string(context, R.string.error_encoder_fallback))
                    )
                }
            }
            videoEncoder?.onOutputBufferAvailable = { data, info ->

                // 仅录制时入缓冲，跳过 CODEC_CONFIG 配置包。
                if (isRecording.get() && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {

                    // 帧时间戳取当前帧自身的采集时刻（单调时钟毫秒）：限帧层把 elapsedRealtimeNanos 写入
                    // eglPresentationTimeANDROID，编码器回传的 presentationTimeUs 即该值（微秒）；
                    // 无渲染层时编码器自行打戳，改用当前单调时刻。
                    lastFrameTimeMs = if (frameRateLimiter != null) {
                        info.presentationTimeUs / 1000L
                    } else {
                        SystemClock.elapsedRealtime()
                    }
                    ringBuffer.put(
                        MediaData(
                            data,
                            info.presentationTimeUs, info.flags,
                            lastFrameTimeMs
                        )
                    )
                }
            }

            var captureWidth: Int
            var captureHeight: Int
            val adaptRotation: Int
            initialScreenLandscape = metrics.widthPixels > metrics.heightPixels
            captureDensityDpi = metrics.densityDpi
            // 内容旋转开启：按真实屏幕尺寸采集，旋转渲染交给限帧层。
            if (settings.contentRotationEnabled) {

                val state = computeAdaptState()
                adaptRotation = state.rotationDegrees
                captureWidth = metrics.widthPixels
                captureHeight = metrics.heightPixels
            } else {
                // 内容旋转关闭：按编码器输出尺寸采集，不做旋转。
                adaptRotation = 0
                captureWidth = videoEncoder!!.getWidth()
                captureHeight = videoEncoder!!.getHeight()
            }

            // 预计算适配状态并重置节流计时起点。
            cachedAdaptState = if (settings.contentRotationEnabled) computeAdaptState() else null
            lastAdaptCheckMs = 0L

            // 限帧层负责按目标帧率渲染并把画面旋转到编码器方向。
            var limiter: FrameRateLimiter? = null
            try {
                limiter = FrameRateLimiter(
                    encoderSurface = checkNotNull(videoEncoder?.getInputSurface()) { "encoder input surface unavailable" },
                    targetFps = settings.frameRate,
                    width = videoEncoder!!.getWidth(),
                    height = videoEncoder!!.getHeight(),
                    inputWidth = captureWidth,
                    inputHeight = captureHeight,
                    rotationDegrees = adaptRotation,
                    rotationProvider = adaptStateProvider,
                    onInputSizeChange = ::resizeVirtualDisplayForAdapt
                ).apply { prepare() }
                Log.d(
                    TAG,
                    "frame limiter enabled: ${settings.frameRate}fps, " +
                            "${videoEncoder!!.getWidth()}x${videoEncoder!!.getHeight()}" +
                            (if (adaptRotation != 0)
                                ", adaptive rotation ${adaptRotation}deg (capture ${captureWidth}x$captureHeight)"
                            else "")
                )
            } catch (e: Exception) {
                // 限帧层不可用时回退直连：帧率由屏幕刷新率决定（MAX_FPS_TO_ENCODER 仅为软约束）。
                Log.w(TAG, "Frame limiter init failed; falling back to direct connection (fps follows screen refresh rate)", e)
                if (settings.contentRotationEnabled) {

                    // 直连无渲染层可旋转画面，采集尺寸改回编码器输出尺寸。
                    Log.w(
                        TAG,
                        "content rotation unavailable in direct fallback (no render layer); " +
                                "capture size reverted to encoder output size"
                    )
                    captureWidth = videoEncoder!!.getWidth()
                    captureHeight = videoEncoder!!.getHeight()
                }
            }
            frameRateLimiter = limiter
            directConnection = limiter == null

            // 容量按回放时长表达，与用户意图一致；帧率仅用于诊断，不再参与容量计算。
            // 分片长度由软/硬上限约束，编码器不补发关键帧时请求补发，仍不发则强制切段。
            ringBuffer = SegmentRingBuffer(
                capacityMs = settings.getReplayDurationMs() + RecorderSettings.BUFFER_MARGIN_SECONDS * 1000L,
                maxBytes = RecorderSettings.MAX_VIDEO_BUFFER_BYTES,
                softSegmentMs = RecorderSettings.SOFT_SEGMENT_MS,
                hardSegmentMs = RecorderSettings.HARD_SEGMENT_MS,
                overrunThrottleMs = RecorderSettings.KEYFRAME_REQUEST_THROTTLE_MS
            ).apply {
                onSegmentOverrun = {
                    Log.d(TAG, "segment overrun: requesting key frame")
                    videoEncoder?.requestKeyFrame()
                }
            }
            // 仅当音频编码器存在时才创建音频缓冲，避免保存时永远为空。
            audioBuffer = if (audioEncoder != null) CycleRingBuffer(
                settings.getAudioBufferCapacity(),
                maxBytes = RecorderSettings.MAX_AUDIO_BUFFER_BYTES,
                sizeOf = { it.data.size }
            ) else null
            ringBuffer.clear()
            audioBuffer?.clear()

            virtualDisplay = mediaProjection.createVirtualDisplay(
                "ScreenRecorder",
                captureWidth,
                captureHeight,
                metrics.densityDpi,
                // AUTO_MIRROR：把主屏内容镜像到目标 Surface。
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                limiter?.getInputSurface() ?: videoEncoder!!.getInputSurface(),
                null, null
            )
            // 个别情况下 createVirtualDisplay 返回 null，按 prepare 失败抛出，由外层统一释放。
            if (virtualDisplay == null) {
                throw Exception(
                    LanguagePrefs.string(context, R.string.error_projection_unavailable)
                )
            }
        } catch (e: Exception) {

            // 失败时清理已创建资源，避免悬挂编码器与显示。
            Log.e(TAG, "prepare failed, releasing created resources", e)
            release()
            throw e
        }
    }

    /** 启动编码器与限帧层，开始接收编码数据入缓冲。 */
    fun start() {
        // 重复调用直接返回，保持幂等。
        if (isRecording.get()) return
        isStopped.set(false)

        try {
            videoEncoder?.start()

            frameRateLimiter?.start()
        } catch (e: Exception) {
            // 启动失败走统一释放路径，避免悬挂资源。
            Log.e(TAG, "Video start failed; cleaning up encoders and display resources", e)
            releasePipelineResources()
            isStopped.set(true)
            throw e
        }
        // 视频成功后再单独启动音频，其失败不影响视频录制。
        try {
            audioEncoder?.start()
        } catch (e: Exception) {
            Log.w(TAG, "Audio start failed; continuing video-only", e)
            try {
                audioEncoder?.stop()
            } catch (_: Exception) {
            }

            try {
                audioEncoder?.release()
            } catch (_: Exception) {
            }
            audioEncoder = null
        }
        // 全部启动成功后才置为录制中并通知主线程。
        isRecording.set(true)
        handler.post { onRecordingStarted?.invoke() }
    }

    /** 停止编码器与限帧层并通知外部；可重复调用。 */
    fun stop() {

        // 原子置位，确保 stop 只生效一次。
        if (isStopped.getAndSet(true)) return
        isRecording.set(false)

        // 限帧层须先于编码器停止，保证输入流水线有序关闭。
        frameRateLimiter?.stop()
        videoEncoder?.stop()
        audioEncoder?.stop()
        handler.post { onRecordingStopped?.invoke() }
    }

    /** 释放全部资源，幂等；isStopped 仍由 stop() 置位。 */
    fun release() {
        releasePipelineResources()
    }

    /** 唯一释放实现：按固定顺序释放全部管线资源；各步单独容错。 */
    private fun releasePipelineResources() {
        isRecording.set(false)

        try {
            virtualDisplay?.release()
        } catch (_: Exception) {
        }
        virtualDisplay = null
        try {
            frameRateLimiter?.stop()
        } catch (_: Exception) {
        }
        frameRateLimiter = null
        directConnection = false
        try {
            videoEncoder?.stop()
        } catch (_: Exception) {
        }
        try {
            audioEncoder?.stop()
        } catch (_: Exception) {
        }
        try {
            videoEncoder?.release()
        } catch (_: Exception) {
        }
        try {
            audioEncoder?.release()
        } catch (_: Exception) {
        }
        videoEncoder = null
        audioEncoder = null
        // 置投影已停止并注销回调，防残留回调触发错误。
        projectionStopped = true

        try {
            mediaProjection.unregisterCallback(projectionCallback)
        } catch (_: Exception) {
        }
    }

    /** 音频编码器是否可用。 */
    fun isAudioActive(): Boolean = audioEncoder != null

    /** 系统投影是否仍存活。 */
    fun isCaptureAlive(): Boolean = !projectionStopped

    /**
     * [timeoutMs] 内渲染线程是否仍在向编码器提交帧，供掉帧/断流检测。
     */
    fun hasRecentVideoOutput(nowMillis: Long, timeoutMs: Long): Boolean = videoOutputActive(
        nowMillis = nowMillis,
        timeoutMs = timeoutMs,
        projectionStopped = projectionStopped,
        directConnection = directConnection,
        limiterRenderedTimeMs = frameRateLimiter?.lastRenderedTimeMs,
        lastFrameTimeMs = lastFrameTimeMs
    )

    /** 已产出的编码帧数：调用方按间隔比对读数是否增长，用于判定排空线程是否仍在推进。 */
    fun getVideoFrameCount(): Int = videoEncoder?.getFrameCount() ?: 0

    /** [timeoutMs] 内是否出现过超阈值的真实音频电平（静音不计）。 */
    fun hasRecentAudioPeak(nowMillis: Long, timeoutMs: Long): Boolean =
        audioPeakActive(nowMillis, timeoutMs, lastAudioPeakTimeMs)

    fun getRingBuffer(): SegmentRingBuffer = ringBuffer
    fun getAudioBuffer(): CycleRingBuffer<MediaData>? = audioBuffer
    fun getVideoFormat(): android.media.MediaFormat? = videoEncoder?.getOutputFormat()
    fun getAudioFormat(): android.media.MediaFormat? = audioEncoder?.getOutputFormat()
    fun getAudioAmplitude(): Float = audioEncoder?.currentAmplitude ?: 0f
}
