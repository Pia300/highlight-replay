package io.github.pia300.highlightreplay.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 引擎错误上报策略的单元测试。 */
class ErrorReportPolicyTest {

    /** 会话正常进行中：引擎上报一律告知用户。 */
    @Test
    fun reportsWhileSessionIsHealthy() {
        assertTrue(
            shouldReportEngineError(
                bySystemStop = false,
                systemStopNotified = false,
                stopping = false,
                sessionTearingDown = false
            )
        )
    }

    /**
     * 采集管线启动期间（`prepare()` 阶段）必须上报。
     *
     * 这是本策略取代 `!isRunning` 的原因：该阶段 `isRunning` 尚未置位，而引擎恰恰在
     * `prepare()` 里上报配置类问题（如指定编码器不可用、已改用系统代选）。
     * 若丢弃条件写成 `!isRunning`，这类提示会在真正开始录制前被吞掉。
     *
     * 策略故意不接收 `isRunning`：启动中与会话健康在这条判据上取值相同，
     * 二者都不该被丢弃。本用例钉住这一点。
     */
    @Test
    fun reportsDuringStartupBeforeIsRunningIsSet() {
        assertTrue(
            shouldReportEngineError(
                bySystemStop = false,
                systemStopNotified = false,
                stopping = false,
                sessionTearingDown = false
            )
        )
    }

    /** 已发起停止：不再打扰用户。 */
    @Test
    fun dropsWhenStopping() {
        assertFalse(
            shouldReportEngineError(
                bySystemStop = false,
                systemStopNotified = false,
                stopping = true,
                sessionTearingDown = false
            )
        )
    }

    /** 会话已进入收尾（含启动失败收尾）：不再打扰用户。 */
    @Test
    fun dropsWhenSessionIsTearingDown() {
        assertFalse(
            shouldReportEngineError(
                bySystemStop = false,
                systemStopNotified = false,
                stopping = false,
                sessionTearingDown = true
            )
        )
    }

    /** 系统终止投影：首次须告知。 */
    @Test
    fun reportsSystemStopOnce() {
        assertTrue(
            shouldReportEngineError(
                bySystemStop = true,
                systemStopNotified = false,
                stopping = false,
                sessionTearingDown = false
            )
        )
    }

    /** 系统终止投影：已提示过则不再重复（onProjectionStopped 与 onError 是同一次终止的两个回调）。 */
    @Test
    fun dropsDuplicateSystemStopNotification() {
        assertFalse(
            shouldReportEngineError(
                bySystemStop = true,
                systemStopNotified = true,
                stopping = true,
                sessionTearingDown = true
            )
        )
    }

    /**
     * 系统终止一到达就会同步把 stopping 置位，故系统终止路径**不能**被 stopping/tearingDown 挡掉；
     * 这正是不把它并入下一条统一条件的原因。
     */
    @Test
    fun systemStopIsNotSuppressedByTeardownFlags() {
        assertTrue(
            shouldReportEngineError(
                bySystemStop = true,
                systemStopNotified = false,
                stopping = true,
                sessionTearingDown = true
            )
        )
    }
}
