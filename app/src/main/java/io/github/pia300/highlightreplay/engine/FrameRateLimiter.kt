package io.github.pia300.highlightreplay.engine

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import java.nio.FloatBuffer

/**
 * 以目标帧率消费输入帧并渲染到编码器 Surface 的帧率限速器。
 *
 * @param encoderSurface 编码器输入 Surface，EGL 以此为渲染目标。
 * @param targetFps 目标输出帧率，用于计算固定槽位间隔。
 * @param width 编码器输出宽（渲染目标尺寸）。
 * @param height 编码器输出高（渲染目标尺寸）。
 * @param inputWidth 输入帧宽，默认与 [width] 相同。
 * @param inputHeight 输入帧高，默认与 [height] 相同。
 * @param rotationDegrees 输入画面旋转角度，仅支持 0 或 90。
 * @param rotationProvider 渲染前查询的最新自适应状态；null 表示不启用自适应。
 * @param onInputSizeChange 自适应状态变化后的回调，参数为输入宽、高。
 */
class FrameRateLimiter(
    private val encoderSurface: Surface,
    targetFps: Int,
    private val width: Int,
    private val height: Int,
    private val inputWidth: Int = width,
    private val inputHeight: Int = height,

    rotationDegrees: Int = 0,

    private val rotationProvider: (() -> AdaptState?)? = null,

    private val onInputSizeChange: ((Int, Int) -> Unit)? = null
) : SurfaceTexture.OnFrameAvailableListener {

    companion object {
        private const val TAG = "FrameRateLimiter"

        /** 无帧可渲染时的轮询间隔（毫秒）。 */
        private const val IDLE_POLL_MS = 10L

        /** 每渲染多少帧输出一次诊断日志。 */
        private const val DIAGNOSTIC_LOG_EVERY = 300L

        /** 顶点着色器：把位置与纹理坐标传给片元着色器。 */
        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec4 aTextureCoordinate;
            varying vec4 vTextureCoordinate;
            void main() {
                gl_Position = aPosition;
                vTextureCoordinate = aTextureCoordinate;
            }
        """

        /** 片元着色器：从外部 OES 纹理采样并输出颜色。 */
        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision highp float;
            varying vec4 vTextureCoordinate;
            uniform samplerExternalOES sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, vTextureCoordinate.xy/vTextureCoordinate.z);
            }
        """
    }

    /** 目标帧间隔（纳秒）。 */
    private val frameIntervalNs: Long =
        (1_000_000_000L / targetFps.coerceIn(1, 240)).coerceAtLeast(1L)

    /** 接收输入帧的 SurfaceTexture，由渲染线程消费。 */
    private var surfaceTexture: SurfaceTexture? = null
    /** 暴露给外部生产者的输入 Surface，包装 surfaceTexture。 */
    private var inputSurface: Surface? = null

    /** EGL 显示连接、渲染上下文与编码器窗口表面的持有者。 */
    private val eglCore = EglCore(encoderSurface)

    private var textureId = 0
    private var program = 0
    private var positionLoc = 0
    private var textureCoordinateLoc = 0
    private var samplerLoc = 0

    private val initialGeometry: RenderGeometry =
        buildRenderGeometry(rotationDegrees, inputWidth, inputHeight, width, height)

    private var positionBuffer: FloatBuffer = directFloatBuffer(
        scaledQuad(initialGeometry.quadScaleX, initialGeometry.quadScaleY)
    )

    private var textureCoordinateBuffer: FloatBuffer =
        directFloatBuffer(initialGeometry.textureCoordinates)

    private var currentRotation = rotationDegrees

    private var currentInputWidth = inputWidth
    private var currentInputHeight = inputHeight

    private var quadScaleX = initialGeometry.quadScaleX
    private var quadScaleY = initialGeometry.quadScaleY

    @Volatile
    private var running = false
    /**
     * 待消费的输入帧数（生产者在 [onFrameAvailable] 自增，渲染线程每槽位原子递减消费一帧）。
     *
     * 必须用原子计数：布尔标志的「读-改-写」不是原子操作，生产者与渲染线程并发时会丢失更新，
     * 使 `updateTexImage` 次数少于入队次数，SurfaceTexture 的缓冲不归还生产者
     * （缓冲池耗尽后画面冻结）。计数用 `getAndUpdate` 保证不丢更新，
     * 且生产者连发多帧时余量留到后续槽位逐帧消费，维持入队/出队守恒。
     */
    private val pendingFrames = java.util.concurrent.atomic.AtomicInteger(0)

    // 渲染线程引用跨线程读写（start() 写、stop() 读以等待退出），故 @Volatile。
    @Volatile
    private var renderThread: Thread? = null

    /** 渲染线程是否实际运行过，决定 EGL 资源的释放路径。 */
    @Volatile
    private var threadRan = false

    /** 已成功提交到编码器的帧数（仅内部诊断统计）。 */
    @Volatile
    private var renderedFrames: Long = 0L

    /** 最近一次渲染完成时间（单调时钟毫秒，只读）；单调时钟不受系统对时/时区调整影响。 */
    @Volatile
    var lastRenderedTimeMs: Long = 0L
        private set

    /** 连续渲染失败次数，用于节流错误日志。 */
    private var consecutiveFailures = 0L

    @Volatile
    private var inputFrames = 0L

    private var renderTimeTotalNs = 0L
    private var lastDiagnosticInput = 0L
    private var lastDiagnosticTimeMs = 0L

    /** 供外部生产者（如 MediaProjection）使用的输入 Surface；须先调用 prepare。 */
    fun getInputSurface(): Surface = checkNotNull(inputSurface) { "FrameRateLimiter not prepared" }

    /** 初始化 EGL 与 SurfaceTexture；失败则清理已分配资源并重新抛出。 */
    fun prepare() {
        try {
            eglCore.init()
            initGlAndSurfaceTexture()
        } catch (e: Exception) {
            Log.e(TAG, "prepare failed, cleaning up EGL resources", e)
            teardown()
            throw e
        }
    }

    /** 启动渲染线程，按目标帧率消费并渲染输入帧。 */
    fun start() {
        if (running) return
        running = true
        // 先发布线程引用再启动：stop() 可观察到该引用并等待线程退出。
        val thread = Thread({
            try {

                // 音频级线程优先级降低渲染调度抖动（设置失败时忽略）。
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            } catch (_: Exception) {
            }
            renderLoop()
        }, "FrameRateLimiter-render")
        renderThread = thread
        thread.start()
    }

    /** 停止渲染并等待渲染线程退出；必要时按兜底路径释放 EGL 资源。 */
    fun stop() {
        running = false
        val t = renderThread
        renderThread = null
        if (t == null) {

            // 线程从未启动：由当前线程执行清理。
            if (!threadRan) teardown()
            return
        }
        // 先等待 2 秒；超时可能是编码器背压阻塞缓冲交换。
        t.join(2000)
        if (!t.isAlive) return
        Log.w(TAG, "Render thread not exiting within 2s (possibly blocked by encoder backpressure); polling until exit")
        val deadline = SystemClock.elapsedRealtime() + 2000
        while (t.isAlive && SystemClock.elapsedRealtime() < deadline) {
            try {
                Thread.sleep(50)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
        if (t.isAlive) {

            // 仍存活：EGL 资源由渲染线程退出路径释放。
            Log.w(TAG, "Render thread still alive; EGL resources will be released on render-thread exit")
        }
    }

    /** SurfaceTexture 帧可用回调：累加输入帧计数与待消费帧数。 */
    override fun onFrameAvailable(surfaceTexture: SurfaceTexture) {
        inputFrames++
        pendingFrames.incrementAndGet()
    }

    /** 在当前 EGL 上下文创建 GL 程序、OES 纹理与 SurfaceTexture，并挂接帧回调。 */
    private fun initGlAndSurfaceTexture() {
        check(eglCore.makeCurrent()) {
            "eglMakeCurrent failed"
        }
        try {
            program = GlUtils.createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            positionLoc = GLES20.glGetAttribLocation(program, "aPosition")
            textureCoordinateLoc = GLES20.glGetAttribLocation(program, "aTextureCoordinate")
            samplerLoc = GLES20.glGetUniformLocation(program, "sTexture")
            textureId = GlUtils.createOesTexture()

            val st = SurfaceTexture(textureId)
            // 默认缓冲尺寸与输入帧一致，避免纹理尺寸不匹配。
            st.setDefaultBufferSize(inputWidth, inputHeight)
            st.setOnFrameAvailableListener(this)
            surfaceTexture = st
            inputSurface = Surface(st)
        } finally {

            // 初始化完成后立即解除当前绑定，避免占用渲染线程所需上下文。
            eglCore.releaseCurrent()
        }
    }

    /** 渲染循环：恒定节拍下取帧、渲染、交换缓冲并提交给编码器。 */
    private fun renderLoop() {

        threadRan = true
        try {
            if (!eglCore.makeCurrent()) {
                Log.e(
                    TAG,
                    "render thread eglMakeCurrent failed (error=0x" +
                            "${Integer.toHexString(EGL14.eglGetError())}), exiting"
                )
                return
            }
            Log.d(
                TAG,
                "renderLoop started, interval=${"%.3f".format(frameIntervalNs / 1e6)}ms, size=${width}x$height"
            )

            var nextSlotNs = SystemClock.elapsedRealtimeNanos()
            var firstFrameRendered = false

            // 诊断基准须在渲染循环起点初始化；保留初值 0 会把进程启动时长算进首个诊断周期。
            lastDiagnosticInput = inputFrames
            lastDiagnosticTimeMs = SystemClock.elapsedRealtime()
            // 输入帧率是「画面是否有新内容」的唯一依据，现场排查断流误报时需要它。
            // 每 DIAGNOSTIC_LOG_EVERY 帧打印一次（约 5-10 秒一行），开销可忽略。

            while (running && !Thread.currentThread().isInterrupted) {
                val nowNs = SystemClock.elapsedRealtimeNanos()
                // 未到槽位：有帧待处理则睡到槽位，否则按空闲间隔轮询。
                if (nowNs < nextSlotNs) {

                    val remainNs = nextSlotNs - nowNs
                    if (firstFrameRendered || pendingFrames.get() > 0) {
                        sleepUntilNs(nextSlotNs)
                    } else {
                        sleepMs(minOf(remainNs / 1_000_000, IDLE_POLL_MS))
                    }
                    continue
                }
                // 未渲染首帧且无新帧：轮询等待首个输入帧。
                if (!firstFrameRendered && pendingFrames.get() <= 0) {

                    sleepMs(IDLE_POLL_MS)
                    continue
                }

                // 单次原子「读取旧值并递减」：旧值 > 0 表示本槽位消费一帧。
                // 不用 getAndSet(0) 再回写（中间会丢生产者的自增），也不拆成两次读取（判定与消费会错位）。
                // 余量（生产者比渲染节拍更快时）留待后续槽位逐帧消费，保证 updateTexImage 与入队次数守恒。
                val consumeNewFrame = pendingFrames.getAndUpdate { if (it > 0) it - 1 else 0 } > 0

                // 以当前时间作为该帧呈现时间戳，编码器据此维持时间线。
                val presentationTimeNs = SystemClock.elapsedRealtimeNanos()

                // 首帧后每个槽位都会调用 renderFrame：有新帧则消费，无新帧则重提上一纹理，维持恒定输出节拍。
                if (renderFrame(consumeNewFrame, presentationTimeNs)) {
                    renderTimeTotalNs += SystemClock.elapsedRealtimeNanos() - presentationTimeNs
                    renderedFrames++
                    if (!firstFrameRendered) {
                        firstFrameRendered = true
                        Log.d(TAG, "First frame submitted to encoder; entering constant cadence")
                    }

                    if (renderedFrames % DIAGNOSTIC_LOG_EVERY == 0L) {
                        val diagnosticNow = SystemClock.elapsedRealtime()
                        val avgRenderMs = renderTimeTotalNs / DIAGNOSTIC_LOG_EVERY / 1_000_000
                        val elapsedMs = (diagnosticNow - lastDiagnosticTimeMs).coerceAtLeast(1L)
                        val inputRate = (inputFrames - lastDiagnosticInput) * 1000L / elapsedMs
                        lastDiagnosticInput = inputFrames
                        lastDiagnosticTimeMs = diagnosticNow
                        renderTimeTotalNs = 0L
                        Log.d(
                            TAG,
                            "rendered $renderedFrames frames, total input=$inputFrames, " +
                                    "recent input rate~${inputRate}fps, avg render=${avgRenderMs}ms"
                        )
                    }
                }

                // 下一槽位 = 本次渲染时刻 + 固定帧间隔（自对齐、不随墙钟漂移）；首帧尽快送出后进入稳定节拍。
                nextSlotNs = presentationTimeNs + frameIntervalNs
            }
            eglCore.releaseCurrent()
        } catch (e: Exception) {
            Log.e(TAG, "renderLoop exception: ${e.message}")
        } finally {
            running = false
            Log.d(TAG, "renderLoop ended, frames=$renderedFrames")

            teardown()
        }
    }

    /** 渲染一帧：先处理自适应更新，再绘制纹理并交换缓冲提交给编码器。 */
    private fun renderFrame(consumeNewFrame: Boolean, presentationTimeNs: Long): Boolean {
        return try {

            // 查询自适应状态；旋转或输入尺寸变化时重建几何、同步缓冲并通知外部。
            rotationProvider?.invoke()?.let { state ->
                if (state.rotationDegrees != currentRotation ||
                    state.inputWidth != currentInputWidth ||
                    state.inputHeight != currentInputHeight
                ) {
                    currentRotation = state.rotationDegrees
                    currentInputWidth = state.inputWidth
                    currentInputHeight = state.inputHeight
                    applyGeometry(currentRotation, currentInputWidth, currentInputHeight)
                    surfaceTexture?.setDefaultBufferSize(currentInputWidth, currentInputHeight)
                    onInputSizeChange?.invoke(currentInputWidth, currentInputHeight)
                    Log.d(
                        TAG,
                        "adaptive update: rot=${currentRotation}deg, input " +
                                "${currentInputWidth}x$currentInputHeight" +
                                if (quadScaleX < 1f || quadScaleY < 1f)
                                    ", letterbox ${"%.3f".format(quadScaleX)}x${"%.3f".format(quadScaleY)}" else ""
                    )
                }
            }
            // SurfaceTexture 未就绪时按渲染失败处理。
            val st = surfaceTexture ?: return false
            // updateTexImage 每个输入帧只能取一次，仅在有新帧时调用；无新帧时重提上一纹理，
            // 维持恒定输出节拍与编码器活性，代价是静止画面重复帧占用码率。
            if (consumeNewFrame) st.updateTexImage()
            GLES20.glUseProgram(program)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES20.glUniform1i(samplerLoc, 0)
            // 位置每顶点 3 个 float（步长 12 字节）；纹理坐标每顶点 4 个 float（步长 16 字节）。
            GLES20.glEnableVertexAttribArray(positionLoc)
            GLES20.glVertexAttribPointer(positionLoc, 3, GLES20.GL_FLOAT, false, 12, positionBuffer)
            GLES20.glEnableVertexAttribArray(textureCoordinateLoc)
            GLES20.glVertexAttribPointer(
                textureCoordinateLoc,
                4,
                GLES20.GL_FLOAT,
                false,
                16,
                textureCoordinateBuffer
            )

            // 信箱模式（宽高比不匹配）时先清屏为黑，保证黑边纯净。
            if (quadScaleX < 1f || quadScaleY < 1f) {
                GLES20.glClearColor(0f, 0f, 0f, 1f)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            }
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(positionLoc)
            GLES20.glDisableVertexAttribArray(textureCoordinateLoc)
            GLES20.glUseProgram(0)

            // 指定呈现时间戳并交换缓冲，把当前帧提交给编码器。
            // 交换失败（如编码器 Surface 已释放）表示该帧未提交：按渲染失败处理，
            // 不刷新 lastRenderedTimeMs，让外层输出活性检测能检出画面中断。
            if (!eglCore.presentAndSwap(presentationTimeNs)) {
                throw IllegalStateException(
                    "eglSwapBuffers failed (error=0x${Integer.toHexString(EGL14.eglGetError())})"
                )
            }
            consecutiveFailures = 0

            lastRenderedTimeMs = SystemClock.elapsedRealtime()
            true
        } catch (e: Exception) {

            // 失败仅节流日志（首次与每第 100 次各输出一次）；渲染持续失败时编码器无输出，
            // 外层流状态检测据此把画面标记为中断（通知与悬浮球指示灯），不会自动停止会话。
            consecutiveFailures++
            if (consecutiveFailures == 1L || consecutiveFailures % 100 == 0L) {
                Log.e(TAG, "renderFrame failed x$consecutiveFailures: ${e.message}")
            }
            false
        }
    }

    /** 睡眠直到目标纳秒时刻，循环中持续响应 running 标志。 */
    private fun sleepUntilNs(targetNs: Long) {
        // 循环计算剩余时间而非一次性长睡，避免错过目标时刻并响应中断。
        while (running) {
            val remainNs = targetNs - SystemClock.elapsedRealtimeNanos()
            if (remainNs <= 0) return
            try {
                Thread.sleep(remainNs / 1_000_000, (remainNs % 1_000_000).toInt())
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            }
        }
    }

    /** 按毫秒睡眠，捕获并传递中断状态。 */
    private fun sleepMs(ms: Long) {
        if (ms <= 0) return
        try {
            Thread.sleep(ms)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    /** 释放 SurfaceTexture、输入 Surface 与全部 EGL 资源（幂等）。 */
    private fun teardown() {
        try {
            surfaceTexture?.release()
        } catch (_: Exception) {
        }
        surfaceTexture = null
        try {
            inputSurface?.release()
        } catch (_: Exception) {
        }
        inputSurface = null
        eglCore.teardown()
    }

    /** 按新的旋转角与输入尺寸重建纹理坐标、信箱缩放与顶点缓冲。 */
    private fun applyGeometry(rotation: Int, inputW: Int, inputH: Int) {
        val g = buildRenderGeometry(rotation, inputW, inputH, width, height)
        textureCoordinateBuffer = directFloatBuffer(g.textureCoordinates)
        quadScaleX = g.quadScaleX
        quadScaleY = g.quadScaleY
        positionBuffer = directFloatBuffer(scaledQuad(g.quadScaleX, g.quadScaleY))
    }
}
