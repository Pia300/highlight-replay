package io.github.pia300.highlightreplay.engine

/**
 * [timeoutMs] 内渲染线程是否仍在向编码器提交帧，供掉帧/断流检测。
 *
 * 判据是渲染输出时刻（[FrameRateLimiter.lastRenderedTimeMs]）而非输入帧回调：
 * `onFrameAvailable` 由屏幕内容更新驱动，静止画面下输入帧率仅 1–2 fps，
 * 而渲染线程仍恒定按目标帧率出帧。用输入帧判定会把静止画面误报为「画面中断」。
 * 渲染线程真停摆（EGL 失败、线程退出）时该时间戳才变陈旧，那才是真正的断流。
 *
 * 直连回退路径（限帧层初始化失败）恒返回 true：此时编码器只在内容变化时收到帧，
 * 静止画面下的「无输出」属正常现象，无法据此判定中断，故不显示中断指示。
 *
 * 代价是这个参数在直连期间失去信息量。故**本函数不是「画面是否活跃」的唯一判据**——
 * 它只回答「渲染侧是否在出帧」，回答不了「排空线程是否还在取走输出」。
 * 两者都必须看：[videoStreamActive] 才是对外展示（通知与悬浮球）应当使用的完整判据。
 *
 * 已知但有意保留的两个退化分支（改动它们需要动调用方参数类型，收益不抵风险）：
 * - 直连期间本函数恒 true（原因见上）；
 * - `directConnection == false && limiterRenderedTimeMs == null` 在单调用点下不可达——
 *   `ScreenRecorder` 里 `directConnection = limiter == null` 与 `frameRateLimiter = limiter`
 *   同步派生，唯一的 false/null 组合出现在释放路径，而该处 `projectionStopped` 已先置位短路。
 *
 * @param limiterRenderedTimeMs 限帧层最近一次渲染输出时刻；null 表示未启用限帧层。
 * @param lastFrameTimeMs 最近一帧视频入缓冲时刻。
 */
internal fun videoOutputActive(
    nowMillis: Long,
    timeoutMs: Long,
    projectionStopped: Boolean,
    directConnection: Boolean,
    limiterRenderedTimeMs: Long?,
    lastFrameTimeMs: Long
): Boolean {
    if (projectionStopped) return false
    if (directConnection) return true
    return if (limiterRenderedTimeMs != null) {
        limiterRenderedTimeMs > 0L && nowMillis - limiterRenderedTimeMs < timeoutMs
    } else {
        lastFrameTimeMs > 0L && nowMillis - lastFrameTimeMs < timeoutMs
    }
}

/** [timeoutMs] 内是否出现过超阈值的真实音频电平（静音不计）。 */
internal fun audioPeakActive(nowMillis: Long, timeoutMs: Long, lastAudioPeakTimeMs: Long): Boolean {
    val t = lastAudioPeakTimeMs
    return t > 0L && nowMillis - t < timeoutMs
}

/**
 * 「画面是否活跃」的**唯一对外判据**：渲染侧在出帧 [videoOutputActive]
 * 且编码器帧数仍在推进 [EncoderProgressTracker.isProgressing]。
 *
 * 两个合取项缺一不可：渲染活跃只反映提交侧，排空线程死亡后编码器不再产出，
 * 而渲染线程在输入缓冲填满前仍会持续提交并刷新渲染时刻，故仅凭渲染活跃发现不了它。
 *
 * 通知与悬浮球**必须**共用本函数：两处各自拼判据会出现「通知说中断、悬浮球说活跃」
 * 这类只有用户能发现的矛盾。
 *
 * @param captureAlive 投影是否仍存活（已撤销授权 / 被系统抢占时为 false）。
 * @param recentVideoOutput [videoOutputActive] 的结果。
 * @param encoderProgressing 编码帧数在超时窗口内是否仍在增长。
 */
internal fun videoStreamActive(
    captureAlive: Boolean,
    recentVideoOutput: Boolean,
    encoderProgressing: Boolean
): Boolean = captureAlive && recentVideoOutput && encoderProgressing

/**
 * 编码帧数推进判定器：按 [timeoutMs] 窗口检测排空线程是否仍在产出编码帧。
 *
 * 渲染活跃只反映提交侧。排空线程停止取走输出后编码器不再产出，而渲染线程在输入缓冲填满前
 * 仍会持续提交并刷新渲染时刻，故仅凭渲染活跃无法发现排空线程死亡，须同时要求帧数推进。
 *
 * 帧计数由编码器持有并在每次会话启动时归零，故每个会话必须以 [reset] 重新建立基准，
 * 否则新会话的低计数相对上一会话的高基准恒判为停滞。
 */
internal class EncoderProgressTracker(private val timeoutMs: Long) {

    private var lastFrameCount = 0
    private var lastAdvanceTimeMs = 0L
    private var hasBaseline = false

    /** 丢弃当前基准，使下一次 [isProgressing] 无条件判为推进并重新建立基准。 */
    fun reset() {
        lastFrameCount = 0
        lastAdvanceTimeMs = 0L
        hasBaseline = false
    }

    /** 无基准或帧数较基准有增长即判为推进并刷新基准；无增长时自基准时刻起的 [timeoutMs] 内仍判为推进。 */
    fun isProgressing(currentFrameCount: Int, nowMs: Long): Boolean {
        if (!hasBaseline || currentFrameCount > lastFrameCount) {
            lastFrameCount = currentFrameCount
            lastAdvanceTimeMs = nowMs
            hasBaseline = true
            return true
        }
        return nowMs - lastAdvanceTimeMs < timeoutMs
    }
}
