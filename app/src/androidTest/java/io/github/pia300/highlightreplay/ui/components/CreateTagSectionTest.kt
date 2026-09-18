package io.github.pia300.highlightreplay.ui.components

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.pia300.highlightreplay.R
import io.github.pia300.highlightreplay.ui.theme.HighlightReplayTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 新建标签区块的输入与提交行为。
 *
 * 该路径在真机上无法用 adb 注入文本（IME 收下按键但未提交到弹窗内的文本框），
 * 只能用 Compose 测试确定性验证；按钮启用条件、提交参数与取消行为同样在此覆盖。
 */
@RunWith(AndroidJUnit4::class)
class CreateTagSectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** 按资源 ID 取当前语言的字符串，避免测试绑定到某一种语言。 */
    private fun str(resId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resId)

    private fun setSection(onCreateTag: (String, Long) -> Boolean) {
        composeRule.setContent {
            HighlightReplayTheme { CreateTagSection(onCreateTag = onCreateTag) }
        }
    }

    /** 展开后名称未填写时不可提交。 */
    @Test
    fun createIsDisabledUntilNameIsEntered() {
        setSection { _, _ -> true }

        composeRule.onNodeWithText(str(R.string.tag_new)).performClick()

        composeRule.onNodeWithText(str(R.string.tag_create)).assertIsNotEnabled()
    }

    /** 输入名称后按钮可用，提交回传去除首尾空白的名称与默认色板首色。 */
    @Test
    fun createPassesTrimmedNameAndDefaultColor() {
        var receivedName: String? = null
        var receivedColor: Long? = null
        setSection { name, color ->
            receivedName = name
            receivedColor = color
            true
        }

        composeRule.onNodeWithText(str(R.string.tag_new)).performClick()
        composeRule.onNodeWithText(str(R.string.tag_name_label)).performTextInput("  work  ")
        composeRule.onNodeWithText(str(R.string.tag_create)).assertIsEnabled().performClick()

        assertEquals("work", receivedName)
        assertEquals(TagPalette[0], receivedColor)
    }

    /** 创建被拒绝（重名）时展示错误提示且不收起创建区域。 */
    @Test
    fun rejectedCreateShowsDuplicateError() {
        setSection { _, _ -> false }

        composeRule.onNodeWithText(str(R.string.tag_new)).performClick()
        composeRule.onNodeWithText(str(R.string.tag_name_label)).performTextInput("dup")
        composeRule.onNodeWithText(str(R.string.tag_create)).performClick()

        composeRule.onNodeWithText(str(R.string.tag_exists)).assertExists()
        composeRule.onNodeWithText(str(R.string.tag_name_label)).assertExists()
    }

    /** 取消收起创建区域，且不触发提交。 */
    @Test
    fun cancelDoesNotSubmit() {
        var called = false
        setSection { _, _ ->
            called = true
            true
        }

        composeRule.onNodeWithText(str(R.string.tag_new)).performClick()
        composeRule.onNodeWithText(str(R.string.tag_name_label)).performTextInput("temp")
        composeRule.onNodeWithText(str(R.string.tag_cancel)).performClick()

        assertFalse(called)
        composeRule.onNodeWithText(str(R.string.tag_name_label)).assertDoesNotExist()
    }
}
