package io.github.pia300.highlightreplay.ui

import io.github.pia300.highlightreplay.data.RecorderSettings
import org.junit.Assert.assertNotNull
import org.junit.Test

/** 校验 RecorderSettings 数值档位常量与文案映射同步：任何档位都必须有对应文案。 */
class SettingOptionLabelsTest {

    @Test
    fun resolutionOptionsAllHaveLabels() {
        RecorderSettings.RESOLUTION_OPTIONS.forEach { v ->
            assertNotNull("unmapped resolution label for $v", SettingOptionLabels.resolution(v))
        }
    }

    @Test
    fun frameRateOptionsAllHaveLabels() {
        RecorderSettings.FRAME_RATE_OPTIONS.forEach { v ->
            assertNotNull("unmapped frame rate label for $v", SettingOptionLabels.frameRate(v))
        }
    }

    @Test
    fun bitRateOptionsAllHaveLabels() {
        RecorderSettings.BIT_RATE_OPTIONS.forEach { v ->
            assertNotNull("unmapped bit rate label for $v", SettingOptionLabels.bitRate(v))
        }
    }

    @Test
    fun replayDurationOptionsAllHaveLabels() {
        RecorderSettings.REPLAY_DURATION_OPTIONS.forEach { v ->
            assertNotNull(
                "unmapped replay duration label for $v",
                SettingOptionLabels.replayDuration(v)
            )
        }
    }
}
