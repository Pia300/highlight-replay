package io.github.pia300.highlightreplay.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 采集就绪判据：保存须能取到全部轨道的输出格式。
 *
 * 仅视频格式就绪即判就绪时，启动窗口内的保存会静默丢弃音轨（有音频帧但无音频格式 → 不注册音轨）。
 */
class CapturePipelineReadyTest {

    @Test
    fun videoReadyWithAudioDisabledIsReady() {
        assertTrue(
            capturePipelineReady(
                videoFormatReady = true,
                audioEnabled = false,
                audioFormatReady = false
            )
        )
    }

    @Test
    fun videoReadyWithAudioEnabledButNoAudioFormatIsNotReady() {
        assertFalse(
            capturePipelineReady(
                videoFormatReady = true,
                audioEnabled = true,
                audioFormatReady = false
            )
        )
    }

    @Test
    fun bothFormatsReadyIsReady() {
        assertTrue(
            capturePipelineReady(
                videoFormatReady = true,
                audioEnabled = true,
                audioFormatReady = true
            )
        )
    }

    @Test
    fun audioFormatAloneIsNotReady() {
        assertFalse(
            capturePipelineReady(
                videoFormatReady = false,
                audioEnabled = true,
                audioFormatReady = true
            )
        )
    }

    @Test
    fun neitherFormatIsNotReady() {
        assertFalse(
            capturePipelineReady(
                videoFormatReady = false,
                audioEnabled = false,
                audioFormatReady = false
            )
        )
    }
}
