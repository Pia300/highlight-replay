package io.github.pia300.highlightreplay.ui.screens.settings

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.pia300.highlightreplay.R
import io.github.pia300.highlightreplay.ui.theme.HighlightReplayTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 摇一摇设置区块的交互：开关回传与快捷档位回传。
 *
 * 传感器判定本身在 JVM 上由 ShakeDetectorTest 覆盖；这里只验证控件与状态之间的契约
 * （哪些点击应该回传、回传什么值），属于 Compose 交互、无法在 JVM 上验证的部分。
 */
@RunWith(AndroidJUnit4::class)
class ShakeToSaveSectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** 按资源 ID 取当前语言的字符串，避免测试绑定到某一种语言。 */
    private fun str(resId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resId)

    private fun setSection(
        enabled: Boolean,
        strength: Int,
        onEnabledChange: (Boolean) -> Unit = {},
        onStrengthChange: (Int) -> Unit = {}
    ) {
        composeRule.setContent {
            HighlightReplayTheme {
                ShakeToSaveSection(
                    enabled = enabled,
                    strength = strength,
                    onEnabledChange = onEnabledChange,
                    onStrengthChange = onStrengthChange
                )
            }
        }
    }

    /** 点击未选中的档位回传该档位力度；点击已选中的档位不重复回传。 */
    @Test
    fun presetChipReportsStrengthOnlyWhenChanged() {
        val received = mutableListOf<Int>()
        setSection(enabled = true, strength = 50, onStrengthChange = { received += it })

        composeRule.onNodeWithText(str(R.string.settings_shake_strength_high)).performClick()
        assertEquals(listOf(75), received)

        // 当前力度仍是 50（测试不持有状态），中档即当前档位，点击不应再次回传。
        composeRule.onNodeWithText(str(R.string.settings_shake_strength_medium)).performClick()
        assertEquals(listOf(75), received)
    }

    /** 开关反映当前状态并可切换；力度以百分比展示当前值。 */
    @Test
    fun switchAndStrengthReflectState() {
        var toggled: Boolean? = null
        setSection(enabled = false, strength = 42, onEnabledChange = { toggled = it })

        composeRule.onNode(isToggleable()).assertIsOff().performClick()
        assertEquals(true, toggled)

        composeRule.onNodeWithText("42%").assertExists()
    }

    /** 说明文字随区块渲染，解释力度方向（越大越难触发）。 */
    @Test
    fun hintIsShown() {
        setSection(enabled = true, strength = 50)
        composeRule.onNodeWithText(str(R.string.settings_shake_hint)).assertExists()
    }
}
