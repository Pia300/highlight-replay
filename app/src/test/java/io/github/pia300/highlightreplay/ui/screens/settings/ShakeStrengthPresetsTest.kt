package io.github.pia300.highlightreplay.ui.screens.settings

import io.github.pia300.highlightreplay.data.RecorderSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 摇一摇快捷档位表：值必须合法且严格递增，否则滑杆会被跳到非法值。 */
class ShakeStrengthPresetsTest {

    @Test
    fun presetsAreLegalAndStrictlyIncreasing() {
        assertEquals("低/中/高三个档位", 3, SHAKE_STRENGTH_PRESETS.size)

        var previous = Int.MIN_VALUE
        SHAKE_STRENGTH_PRESETS.forEach { (value, labelRes) ->
            assertTrue("档位值 $value 越界", value in RecorderSettings.SHAKE_STRENGTH_RANGE)
            assertTrue("档位值必须严格递增：$value 不大于 $previous", value > previous)
            assertTrue("档位缺少文案资源", labelRes != 0)
            previous = value
        }
    }

    /** 中档即默认力度：改默认值时必须同步档位，否则默认值在界面上没有任何档位高亮。 */
    @Test
    fun mediumPresetMatchesDefaultStrength() {
        assertEquals(RecorderSettings.SHAKE_STRENGTH_DEFAULT, SHAKE_STRENGTH_PRESETS[1].first)
    }
}
