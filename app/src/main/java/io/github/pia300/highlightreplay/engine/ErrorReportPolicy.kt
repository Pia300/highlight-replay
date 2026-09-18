package io.github.pia300.highlightreplay.engine

/**
 * 引擎上报的错误是否应当告知用户。
 *
 * 从 `RecorderService` 的 onError 回调里抽出成纯函数，使「什么情况下丢弃上报」这条
 * 分支逻辑可被单元测试覆盖——原先它内联在 `mainHandler.post` 的 lambda 中，
 * 只有真机跑到特定时序才可能发现判断错误。
 *
 * @param bySystemStop 本次收尾的原因是「投影被系统终止」（用户不知情，须告知）。
 * @param systemStopNotified 本会话是否已就系统终止提示过（避免与 onError 重复弹）。
 * @param stopping 用户/服务已发起停止。
 * @param sessionTearingDown 本会话已进入收尾（含采集管线启动失败）。
 */
internal fun shouldReportEngineError(
    bySystemStop: Boolean,
    systemStopNotified: Boolean,
    stopping: Boolean,
    sessionTearingDown: Boolean
): Boolean {
    // 系统终止路径由 onProjectionStoppedBySystem 直接提示，此处只负责补一次
    // （其后的 onError 与前者是同一次终止的两个回调，不该弹第二遍）。
    if (bySystemStop) return !systemStopNotified
    // 非系统终止：只在会话仍活着时报。
    //
    // 丢弃条件是"已进入收尾"，**不能**写成 `!isRunning`：会话在引擎线程执行
    // prepare()/start() 期间 isRunning 尚未置位，而引擎恰恰在 prepare() 阶段
    // 上报配置类问题（如指定编码器不可用、已改用系统代选）——用 !isRunning 会把
    // 这些上报在真正开始录制前吞掉。
    return !stopping && !sessionTearingDown
}
