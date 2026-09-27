package io.github.pia300.highlightreplay.service

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import io.github.pia300.highlightreplay.R
import io.github.pia300.highlightreplay.data.AudioSourceMode
import io.github.pia300.highlightreplay.data.LanguagePrefs
import io.github.pia300.highlightreplay.data.RecorderSettings
import io.github.pia300.highlightreplay.engine.EncoderProgressTracker
import io.github.pia300.highlightreplay.engine.ReplaySaver
import io.github.pia300.highlightreplay.engine.ScreenRecorder
import io.github.pia300.highlightreplay.engine.shouldReportEngineError
import io.github.pia300.highlightreplay.engine.videoStreamActive
import io.github.pia300.highlightreplay.ui.ToastCenter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.StateFlow

/** 录制会话对外状态快照（单一状态源）。平台一次 MediaProjection 令牌仅支持一个连续会话，故采用单会话模型，停止即释放全部资源。 */
data class RecorderState(
    val isRunning: Boolean = false,
    val isSaving: Boolean = false,
    val elapsedSeconds: Long = 0L,
    // 录制中修改过设置（需停止并重新录制才能生效）。
    val settingsStale: Boolean = false
)

/** 录制前台服务：画面持续编码进环形缓冲，随时可保存回放；结束即释放全部资源。 */
class RecorderService : Service() {

    companion object {
        private const val TAG = "RecorderService"

        /** 启动录制（携带投影授权结果）。 */
        const val ACTION_START = "action_start"
        const val ACTION_STOP = "action_stop"
        const val ACTION_TRIGGER_REPLAY = "action_trigger_replay"
        const val ACTION_REFRESH_NOTIFICATION = "action_refresh_notification"
        /** 悬浮球显隐开关（由通知按钮触发）。 */
        const val ACTION_TOGGLE_FLOATING = "action_toggle_floating"

        /** 磁贴刷新广播（包级发送，磁贴动态注册监听）。 */
        const val ACTION_TILE_UPDATE = "io.github.pia300.highlightreplay.TILE_UPDATE"
        /** 帧活动广播（悬浮球监听；包级）。 */
        const val ACTION_FRAME_ACTIVITY = "io.github.pia300.highlightreplay.FRAME_ACTIVITY"
        /** 进程内动态广播的签名级权限：Android 13- 动态接收器默认对所有应用开放，注册时以同签名约束发送方。 */
        const val INTERNAL_BROADCAST_PERMISSION =
            "io.github.pia300.highlightreplay.permission.INTERNAL_BROADCAST"

        /** 投影授权结果码。 */
        const val EXTRA_RESULT_CODE = "result_code"
        /** 投影授权数据。 */
        const val EXTRA_RESULT_DATA = "result_data"
        /** 帧活动广播的视频活跃 extra。 */
        const val EXTRA_VIDEO_ACTIVE = "video_active"
        /** 帧活动广播的音频振幅 extra。 */
        const val EXTRA_AUDIO_AMPLITUDE = "audio_amplitude"

        /** 帧活动广播节流（毫秒）。 */
        const val FRAME_BROADCAST_INTERVAL_MS = 200L

        /** 会话 tick 每秒的拍数（tick 基频 = [FRAME_BROADCAST_INTERVAL_MS]）。 */
        const val SESSION_TICKS_PER_SECOND = 5L
        /** 帧活动广播的振幅发送阈值：变化小于该值不重发（0..1 量纲）。 */
        const val AMPLITUDE_SEND_EPSILON = 0.01f
        /** 渲染输出断流容忍（毫秒）：渲染线程停止向编码器提交帧超此时长即判为画面中断。 */
        const val VIDEO_STALL_TIMEOUT_MS = 3000L

        /** 编码帧数判定的采样间隔上限：超过该时长帧数未增长即视为排空线程已停止推进。 */
        const val ENCODER_PROGRESS_TIMEOUT_MS = 1000L

        /** 音频无输入容忍（毫秒）：真实电平缺失超此时长才提示无声音输入。 */
        const val AUDIO_SILENCE_TIMEOUT_MS = 7000L
        /** “已保存”标题显示时长（毫秒）后恢复默认标题。 */
        const val TITLE_RESET_DELAY_MS = 2500L

        // ---- 全局运行状态（单一来源；磁贴/悬浮球/绑定方无实例时读取）----
        val isRunning: Boolean get() = RecorderRuntimeState.isRunning
        val isSaving: Boolean get() = RecorderRuntimeState.isSaving

        /** 采集管线真正启动后置位（授权页据此保持前台；Android 15 无前台 Activity 时类型化 startForeground 会被降级并终止投影）。 */
        var captureReady: Boolean
            get() = RecorderRuntimeState.captureReady
            private set(value) {
                RecorderRuntimeState.captureReady = value
            }

        val stateFlow: StateFlow<RecorderState> get() = RecorderRuntimeState.stateFlow

        fun currentState(): RecorderState = RecorderRuntimeState.currentState()

        /** 录制中修改设置的进程内通知入口：Toast 提示并置 settingsStale。 */
        fun notifySettingsChangedWhileRecording() {
            if (!RecorderRuntimeState.isRunning) return
            val service = RecorderRuntimeState.instance ?: return
            service.mainHandler.post {
                if (!currentState().isRunning) return@post
                ToastCenter.show(service, service.str(R.string.control_settings_changed_hint), Toast.LENGTH_LONG)
                service.publishState(
                    currentState().copy(settingsStale = true),
                    notifyTile = false
                )
            }
        }
    }

    /** 引擎执行线程：ScreenRecorder 的启动/停止都在这里跑（避免阻塞主线程）。 */
    private val engineExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "RecorderEngine").apply { priority = Thread.NORM_PRIORITY }
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    /** 会话自增 ID：过滤过期回调。 */
    private var sessionId = 0

    // ---- 会话资源（主线程与引擎线程跨线程读写，标 volatile）----
    @Volatile
    private var mediaProjection: MediaProjection? = null
    @Volatile
    private var screenRecorder: ScreenRecorder? = null
    // 引擎线程重建（startCapturePipeline）、主线程读取（triggerReplay/stopRecording），故 @Volatile。
    @Volatile
    private var replaySaver: ReplaySaver? = null
    private var settings: RecorderSettings? = null
    // 会话起点：elapsedRealtime 单调时钟，0 表示未开始。
    private var sessionStartMs = 0L

    @Volatile private var stopping = false
    @Volatile private var startInFlight = false

    /** 编码帧数推进判定器，由主线程独占使用；每次会话启动重新建立基准。 */
    private val encoderProgress = EncoderProgressTracker(ENCODER_PROGRESS_TIMEOUT_MS)

    /** 停止时仍有在途保存：保留前台服务与通知，待保存回调收尾后再撤销（防进程被回收丢文件）。 */
    @Volatile
    private var pendingForegroundTeardown = false

    /** 引擎是否已释放本会话的投影：前台服务须活到此刻，否则系统会提前终止投影并回调假错误。 */
    @Volatile
    private var projectionReleased = false

    /** 停止收尾是否仍在引擎线程执行：期间不得停服，否则系统会因 mediaProjection 前台状态消失而提前终止投影。 */
    @Volatile
    private var teardownInFlight = false

    /**
     * 本会话是否已进入收尾（用户主动停止、被系统终止，或采集管线启动失败）。
     *
     * `isRunning` 不足以代替它：会话在 `engineExecutor` 上调用 `ScreenRecorder.prepare()`/`start()`
     * 期间，`publishState(isRunning = true)` 尚未执行，此时 `isRunning` 仍为 false——
     * 而引擎在 `prepare()` 阶段就会上报配置类问题（如编码器不可用改用系统代选）。
     * 若沿用 `!isRunning` 作丢弃条件，这些上报会在真正开始录制前被吞掉。
     *
     * 置位点与语义对齐：与 `isRunning=false`、`sessionStartMs=0`、`captureReady=false` 同处设置。
     */
    @Volatile
    private var sessionTearingDown = false

    /**
     * 「画面是否活跃」的唯一判据（单一来源）。
     *
     * 通知与悬浮球**共用本 lambda**：两处各自拼判据会出现「通知说中断、悬浮球说活跃」
     * 这种只有用户能发现的矛盾。判据本体在 engine/CaptureLiveness.videoStreamActive。
     */
    private val videoActiveProvider: () -> Boolean = {
        val sr = screenRecorder
        if (sr == null) {
            false
        } else {
            val now = SystemClock.elapsedRealtime()
            videoStreamActive(
                captureAlive = sr.isCaptureAlive(),
                recentVideoOutput = sr.hasRecentVideoOutput(now, VIDEO_STALL_TIMEOUT_MS),
                encoderProgressing = encoderProgress.isProgressing(sr.getVideoFrameCount(), now)
            )
        }
    }

    private val notificationController = RecordingNotificationController(
        service = this,
        mainHandler = mainHandler,
        audioEnabled = { settings?.hasAudio() ?: false },
        isSessionStopping = { stopping },
        videoActiveProvider = videoActiveProvider,
        audioActiveProvider = {
            val sr = screenRecorder
            if (sr == null || !sr.isAudioActive()) false
            else {
                val now = SystemClock.elapsedRealtime()
                // 静音时系统仍产出零 PCM，须有真实电平才算有输入；容忍短暂静音防闪烁。
                sr.hasRecentAudioPeak(now, AUDIO_SILENCE_TIMEOUT_MS)
            }
        },
        onTileUpdate = { broadcastTileUpdate() }
    )

    private val frameActivityBroadcaster = FrameActivityBroadcaster(
        service = this,
        mainHandler = mainHandler,
        sessionStartMsProvider = { sessionStartMs },
        screenRecorderProvider = { screenRecorder },
        captureReadyProvider = { captureReady },
        videoActiveProvider = videoActiveProvider,
        onCaptureReady = {
            captureReady = true
            notificationController.refresh()
        },
        publish = { state, notifyTile -> publishState(state, notifyTile) }
    )

    private val replaySaveCoordinator = ReplaySaveCoordinator(
        service = this,
        mainHandler = mainHandler,
        notificationController = notificationController,
        replaySaverProvider = { replaySaver },
        screenRecorderProvider = { screenRecorder },
        settingsProvider = { settings },
        publish = { publishState(it) },
        notifyError = { notifyError(it) },
        finishPendingForegroundTeardown = { finishPendingForegroundTeardownIfIdle() }
    )

    private val floatingWindowController = FloatingWindowController(
        service = this,
        notificationController = notificationController
    )

    // ---------------- 生命周期 ----------------

    override fun onCreate() {
        super.onCreate()
        RecorderRuntimeState.instance = this
        NotificationFactory.createChannel(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        RecorderRuntimeState.instance = null
        // 只停周期任务；在途保存由保存线程自行收尾并复位进程级 isSaving。
        stopSessionTickers()
        safeStopRecording()
        engineExecutor.shutdown()
    }

    /** 用户划掉最近任务：主动停止录制（隐私兜底：后台录屏不应无人知晓地继续）。 */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        safeStopRecording()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 非粘性：投影令牌随进程死亡失效，系统重启也无法恢复录制。
        if (intent == null) {
            return START_NOT_STICKY
        }
        when (intent.action) {
            ACTION_START -> handleStartCommand(intent)
            ACTION_STOP -> safeStopRecording()
            ACTION_TRIGGER_REPLAY -> replaySaveCoordinator.triggerReplay()
            ACTION_TOGGLE_FLOATING -> floatingWindowController.toggleFloatingVisibility()
            ACTION_REFRESH_NOTIFICATION -> notificationController.refresh(notifyTile = true)
            else -> Log.w(TAG, "Unknown action: ${intent.action}")
        }
        // 会话外被残留命令拉起即自我终止（无前台通知不应空转）。
        // 两种情形必须留活到收尾完成：在途保存（防写盘被随服务销毁打断）、停止收尾
        // （mediaProjection 前台状态随服务销毁消失，系统会提前终止投影并回调假错误）。
        if (!isRunning && !currentState().isSaving && !teardownInFlight && !pendingForegroundTeardown) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    // ---------------- 启动 ----------------

    private fun handleStartCommand(intent: Intent) {
        if (isRunning || startInFlight) {
            Log.w(TAG, "Session already running/starting; ignoring duplicate ACTION_START")
            return
        }
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
        val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        if (resultCode != android.app.Activity.RESULT_OK || resultData == null) {
            Log.e(TAG, "Invalid start params (resultCode=$resultCode)")
            stopSelf()
            return
        }
        startRecording(resultCode, resultData)
    }

    /** 用授权结果开启会话：设置快照仅会话开始时读取一次，录制中修改一律下次生效。 */
    private fun startRecording(resultCode: Int, resultData: Intent) {
        // 停止收尾中拒绝新会话（旧停止任务仍持有令牌/引擎引用）。
        if (stopping) {
            notifyError(str(R.string.toast_stopping_recording))
            return
        }
        startInFlight = true
        sessionId++
        captureReady = false
        // 停止原因与提示标志属上一会话，新会话从未被系统终止的状态起步。
        stopReason = StopReason.USER
        systemStopNotified = false
        // 编码器帧计数随会话归零，基准须同步复位。
        encoderProgress.reset()
        // 新会话重新持有前台服务：清除上一会话遗留的收尾与投影释放标记。
        pendingForegroundTeardown = false
        projectionReleased = false
        teardownInFlight = false
        sessionTearingDown = false
        // 标题覆盖属于已结束的上一会话，本地通知一律用默认标题。
        notificationController.clearTitle()

        val loaded = RecorderSettings.fromPreferences(this)
        // 服务侧最终校验：录音权限缺失时本会话降级为无音频，不写回持久化设置。
        val audioMissing = loaded.hasAudio() &&
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED
        settings = if (audioMissing) {
            loaded.copy(audioSource = AudioSourceMode.NONE.prefValue)
        } else loaded
        if (audioMissing) {
            notifyError(str(R.string.error_audio_permission_missing))
        }

        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        // 前台须先于取令牌就绪：无前台 Activity 时 mediaProjection 类型的前台服务会被系统降级，投影随即终止。
        val projection: MediaProjection?
        try {
            ensureForeground()
            projection = projectionManager.getMediaProjection(resultCode, resultData)
        } catch (e: SecurityException) {
            Log.e(TAG, "Projection token request rejected", e)
            notifyError(str(R.string.error_projection_start_rejected))
            cleanupStartFailure()
            return
        } catch (e: Exception) {
            Log.e(TAG, "Start exception", e)
            notifyError(str(R.string.error_recording_start_failed))
            cleanupStartFailure()
            return
        }

        // 平台允许返回 null：无有效令牌时按失败收尾。
        if (projection == null) {
            Log.e(TAG, "getMediaProjection returned null")
            notifyError(str(R.string.error_projection_unavailable))
            cleanupStartFailure()
            return
        }
        mediaProjection = projection

        // 前一会话的在途保存保留 isSaving，由保存回调真正完成时复位。
        publishState(RecorderState(isRunning = true, isSaving = currentState().isSaving))
        startInFlight = false
        floatingWindowController.ensureFloatingService(started = true)
        notificationController.refresh()
        broadcastTileUpdate()
        engineExecutor.execute { startCapturePipeline() }
    }

    /** 启动失败收尾：清除启动标记与残留令牌引用，刷新磁贴并停服。 */
    private fun cleanupStartFailure() {
        startInFlight = false
        mediaProjection = null
        broadcastTileUpdate()
        stopSelf()
    }

    // ---------------- 录制中：采集管线启停（引擎线程） ----------------

    /** 采集管线入口（引擎线程）：创建并启动 ScreenRecorder，随后重建 ReplaySaver。 */
    private fun startCapturePipeline() {
        // stopping 可能已在主线程置位（停止任务与其同队列），此时放弃创建管线。
        if (stopping) return
        val session = sessionId
        try {
            if (screenRecorder == null) {
                val created = ScreenRecorder(this, checkNotNull(settings), checkNotNull(mediaProjection)).apply {
                    prepare()
                    onRecordingStarted = { if (session == sessionId) onEngineRecordingStarted() }
                    onProjectionStopped = { if (session == sessionId) onProjectionStoppedBySystem() }
                    onRecordingStopped = { if (session == sessionId) onEngineRecordingStopped() }
                    onError = { e ->
                        if (session == sessionId) {
                            mainHandler.post {
                                // ScreenRecorder 在投影停止时先投递 onProjectionStopped、再投递本回调，
                                // 前者已同步把 stopping 置位；因此"正在停止"不足以判定这次错误无需上报，
                                // 还须放行系统终止投影这一种原因，否则用户看不到录制为何结束。
                                val bySystem =
                                    stopReason == StopReason.PROJECTION_STOPPED_BY_SYSTEM
                                if (!shouldReportEngineError(
                                        bySystemStop = bySystem,
                                        systemStopNotified = systemStopNotified,
                                        stopping = stopping,
                                        sessionTearingDown = sessionTearingDown
                                    )
                                ) {
                                    // 被丢弃的上报必须留痕：原先这条路径一行日志都没有，
                                    // 现场排查"错误提示为什么没出现"时无从下手。
                                    Log.w(
                                        TAG,
                                        "Engine error dropped (session already ending): " +
                                                "bySystemStop=$bySystem, stopping=$stopping, " +
                                                "tearingDown=$sessionTearingDown, message=${e.message}"
                                    )
                                    return@post
                                }
                                if (bySystem) systemStopNotified = true
                                notifyError(e.message ?: str(R.string.error_recording_generic))
                                // 编码管线已不可用：停止会话，避免界面继续显示"录制中"。
                                safeStopRecording()
                            }
                        }
                    }
                    start()
                }
                screenRecorder = created
            }
            replaySaver = replaySaveCoordinator.createReplaySaver()
            mainHandler.post {
                if (session != sessionId || stopping) return@post
                notificationController.refresh()
                startSessionTickers()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start capture pipeline", e)
            mainHandler.post {
                if (session != sessionId) return@post
                // 先置收尾标记再上报：否则本次启动失败的提示会被 onError 的丢弃条件吞掉。
                sessionTearingDown = true
                notifyError(e.message ?: str(R.string.error_recording_start_failed))
                safeStopRecording()
            }
        }
    }

    /** ScreenRecorder.start() 的 onRecordingStarted（主线程）：仅建立会话计时起点。 */
    private fun onEngineRecordingStarted() {
        // 停止后的迟到回调（ScreenRecorder 自持 Handler 投递）不得复位状态/标志。
        if (stopping) return
        if (sessionStartMs == 0L) {
            // 与 tick 计时同用单调时钟 elapsedRealtime，避免时间回拨致时长倒退。
            sessionStartMs = SystemClock.elapsedRealtime()
        }
        // captureReady 不在此置位：须等编码器产出输出格式后由 sessionTick 置位（见该处注释）。
        notificationController.refresh()
    }

    /**
     * 引擎 stop 完成回调（主线程）：非主动停止时补齐停服流程。
     *
     * 会话结束的确认提示在此发出：主动停止（按钮、通知、磁贴、悬浮球）与系统终止都经由此处收尾，
     * 故四条入口给出一致的"已停止"反馈。系统终止另有更具体的原因提示，此处不重复。
     */
    private fun onEngineRecordingStopped() {
        if (!stopping) {
            safeStopRecording()
        }
        if (stopReason != StopReason.PROJECTION_STOPPED_BY_SYSTEM) {
            notifyRecordingStopped()
        }
    }

    // ---------------- 停止 ----------------

    /**
     * ScreenRecorder 报告投影已被系统终止（撤销授权、系统抢占等）。
     *
     * 这是唯一一类用户不知情的会话结束，故与主动停止分开处理：先记录原因，使紧随其后的
     * onError 不被"正在停止"的守卫挡掉；若收尾尚未开始（本回调是首个到达者），同时直接提示
     * ——此时 onError 尚未投递，提示不能只依赖它。
     */
    private fun onProjectionStoppedBySystem() {
        val firstToNotice = !stopping
        stopReason = StopReason.PROJECTION_STOPPED_BY_SYSTEM
        if (firstToNotice) {
            systemStopNotified = true
            notifyProjectionStoppedBySystem()
        }
        safeStopRecording()
    }

    /**
     * 提示用户"录制被系统终止"。
     *
     * 文案优先取资源；资源解析不可用时退回硬编码文本，保证任何情况下用户都能知道录制为何结束
     * ——静默结束是这条路径上最坏的失败方式。硬编码文本仅作兜底，不参与正常显示。
     */
    private fun notifyProjectionStoppedBySystem() {
        val text = LanguagePrefs.string(this, R.string.replay_error_projection_stopped)
        ToastCenter.show(
            this,
            text.ifBlank { "Screen recording was stopped by the system" },
            Toast.LENGTH_LONG
        )
    }

    /**
     * 提示用户"录制已停止"（主动停止路径）。
     *
     * 文案优先取资源；资源解析不可用时退回硬编码文本，与系统终止路径同样不依赖资源解析。
     */
    private fun notifyRecordingStopped() {
        val text = LanguagePrefs.string(this, R.string.toast_recording_stopped)
        ToastCenter.show(
            this,
            text.ifBlank { "Recording stopped" },
            Toast.LENGTH_SHORT
        )
    }

    private fun safeStopRecording() {
        try {
            stopRecording()
        } catch (e: Exception) {
            Log.e(TAG, "stopRecording error: ${e.message}", e)
        }
    }

    private fun stopRecording() {
        if (stopping) return
        if (!currentState().isRunning && screenRecorder == null && mediaProjection == null) return

        stopping = true
        // 未被 onProjectionStoppedBySystem 标记过即视为用户主动停止。
        if (stopReason != StopReason.PROJECTION_STOPPED_BY_SYSTEM) {
            stopReason = StopReason.USER
        }
        // 本会话编号快照：延迟回调据此判断期间是否已开启新会话。
        val session = sessionId
        // 收尾期间禁止停服：见 teardownInFlight 声明处。
        teardownInFlight = true
        // 只停周期任务；在途保存由保存线程自行收尾并复位进程级 isSaving。
        stopSessionTickers()

        // 捕获引用的置空与 stop/release 统一放引擎线程串行执行，与启动互斥。
        replaySaver = null
        sessionStartMs = 0L
        captureReady = false
        // 与上三个标记同处置位：此后引擎上报的错误视为收尾噪音，不再打扰用户。
        sessionTearingDown = true
        projectionReleased = false
        notificationController.discardForStop()

        // 保留在途保存的 isSaving，由保存回调完成时复位。
        publishState(currentState().copy(isRunning = false))
        // 前台服务与通知须活到投影释放之后：投影仍存活时撤销 mediaProjection 类型前台服务，
        // 系统会先行终止投影并回调 onStop（会话正常停止被报成"被系统停止"）。
        // 在途保存另需保留前台至写盘完成（防进程被回收丢文件），由保存回调收尾。
        pendingForegroundTeardown = currentState().isSaving
        floatingWindowController.ensureFloatingService(started = false)

        engineExecutor.execute {
            val sr = screenRecorder
            val mp = mediaProjection
            screenRecorder = null
            mediaProjection = null
            try {
                sr?.stop()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping engine", e)
            }
            try {
                sr?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing engine", e)
            }
            try {
                mp?.stop()
            } catch (e: Exception) {
                Log.w(TAG, "projection stop failed: ${e.message}")
            }
            mainHandler.post {
                // 投影已释放：撤销前台并停服；仍有在途保存时仅撤销前台，停服由保存回调收尾。
                if (session == sessionId) projectionReleased = true
                teardownInFlight = false
                finishForegroundTeardownIfIdle()
                stopping = false
                if (!pendingForegroundTeardown) stopSelf()
            }
        }
    }

    /** 引擎释放投影后撤销前台通知；在途保存需保留前台（保活进程防丢文件），此时只等保存回调收尾。 */
    private fun finishForegroundTeardownIfIdle() {
        if (!projectionReleased) return
        if (currentState().isSaving) return
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    /** 会话已停且保存收尾后，才真正撤销前台通知并停服（保存期间保活进程，防文件丢失）。 */
    private fun finishPendingForegroundTeardownIfIdle() {
        if (!pendingForegroundTeardown) return
        val s = currentState()
        if (s.isRunning || s.isSaving) return
        pendingForegroundTeardown = false
        if (RecorderRuntimeState.instance === this) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        stopSelf()
    }

    // ---------------- 通知与状态 ----------------

    private fun ensureForeground() {
        val notification = notificationController.createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationFactory.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NotificationFactory.NOTIFICATION_ID, notification)
        }
    }

    /** 单一状态发布点：字段、StateFlow 与磁贴广播一次到位。 */
    private fun publishState(newState: RecorderState, notifyTile: Boolean = true) {
        RecorderRuntimeState.publish(newState, notifyTile) { broadcastTileUpdate() }
    }

    private fun broadcastTileUpdate() {
        sendBroadcast(Intent(ACTION_TILE_UPDATE).apply { setPackage(packageName) })
    }

    private fun startSessionTickers() {
        frameActivityBroadcaster.startTick()
        notificationController.startStreamMonitor()
    }

    private fun stopSessionTickers() {
        notificationController.stopStreamMonitor()
        frameActivityBroadcaster.stopTick()
    }

    /** 显示错误提示；主线程直接显示，其余线程投递到主线程后显示。 */
    private fun notifyError(message: String) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            ToastCenter.show(this, message, Toast.LENGTH_LONG)
        } else {
            mainHandler.post {
                ToastCenter.show(this, message, Toast.LENGTH_LONG)
            }
        }
    }

    /** 会话停止原因；仅用于决定是否向用户提示，不参与资源收尾。 */
    private enum class StopReason {
        /** 用户主动停止（界面按钮、通知按钮、磁贴、任务被划掉）。 */
        USER,

        /** 投影被系统终止（撤销授权、系统抢占等）：用户不知情，须提示。 */
        PROJECTION_STOPPED_BY_SYSTEM
    }

    /** 本会话的停止原因；在 [`stopRecording`] 起始处按发起方设置。 */
    @Volatile
    private var stopReason = StopReason.USER

    /**
     * 本会话是否已就"投影被系统终止"提示过用户。
     *
     * 投影停止时 ScreenRecorder 先投递 onProjectionStopped、再投递 onError，两者都会走到这里，
     * 故用本标志保证用户只看到一条提示。
     */
    @Volatile
    private var systemStopNotified = false

    private fun str(resId: Int): String = LanguagePrefs.string(this, resId)
}
