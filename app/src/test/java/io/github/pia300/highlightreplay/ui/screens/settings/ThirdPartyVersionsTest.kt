package io.github.pia300.highlightreplay.ui.screens.settings

import io.github.pia300.highlightreplay.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 第三方组件版本声明的完整性契约。
 *
 * 许可证页与 THIRD_PARTY_NOTICES 展示的版本号是手写声明，历史上漂移过一次
 * （声明 lifecycle 2.8.3 而实际解析到 2.9.4）。此处把「声明」钉成会失败的断言：
 * 升级依赖时若只改 build.gradle 而未同步版本常量，或漏掉某个组件，测试即失败。
 */
class ThirdPartyVersionsTest {

    /** 版本号必须是主版本.次版本[.修订] 的纯数字形态，不含 BOM/后缀，便于与依赖坐标逐字对照。 */
    @Test
    fun versionsArePlainNumericVersions() {
        val pattern = Regex("""^\d+\.\d+(\.\d+)?$""")
        thirdPartyVersionList.forEach { (name, version) ->
            assertTrue(
                "$name 的版本声明 '$version' 不是纯数字版本号",
                pattern.matches(version)
            )
        }
    }

    /** 每个组件都必须有非空版本，避免漏填导致界面出现空白版本号。 */
    @Test
    fun everyComponentHasNonBlankVersion() {
        assertTrue("版本清单不应为空", thirdPartyVersionList.isNotEmpty())
        thirdPartyVersionList.forEach { (name, version) ->
            assertTrue("组件名不应为空", name.isNotBlank())
            assertTrue("$name 的版本号不应为空", version.isNotBlank())
        }
    }

    /** 组件名不重复：同一条目出现两次往往意味着改了一处漏了另一处。 */
    @Test
    fun componentNamesAreUnique() {
        val names = thirdPartyVersionList.map { it.first }
        assertEquals("组件名存在重复：$names", names.size, names.toSet().size)
    }

    /**
     * 展示值必须逐字来自 BuildConfig 常量。
     *
     * 这是本测试的核心：它把「界面展示的版本」与「app/build.gradle 的声明」绑成同一真源，
     * 任何一侧单独改动都会被此断言拦下。
     */
    @Test
    fun displayedVersionsComeFromBuildConfigConstants() {
        val byName = thirdPartyVersionList.toMap()
        assertEquals(BuildConfig.DEP_COMPOSE, byName["Compose UI"])
        assertEquals(BuildConfig.DEP_MATERIAL3, byName["Material3"])
        assertEquals(BuildConfig.DEP_ICONS, byName["Material Icons"])
        assertEquals(BuildConfig.DEP_ACTIVITY, byName["Activity"])
        assertEquals(BuildConfig.DEP_LIFECYCLE, byName["Lifecycle"])
        assertEquals(BuildConfig.DEP_COREKTX, byName["Core KTX"])
        assertEquals(BuildConfig.DEP_SAVEDSTATE, byName["SavedState"])
        assertEquals(BuildConfig.DEP_TRACING, byName["Tracing"])
    }

    /** BuildConfig 常量不得为空串：空值会让上面那条断言变成无意义的自我比较。 */
    @Test
    fun buildConfigVersionConstantsArePopulated() {
        val constants = listOf(
            "DEP_COMPOSE" to BuildConfig.DEP_COMPOSE,
            "DEP_MATERIAL3" to BuildConfig.DEP_MATERIAL3,
            "DEP_ICONS" to BuildConfig.DEP_ICONS,
            "DEP_ACTIVITY" to BuildConfig.DEP_ACTIVITY,
            "DEP_LIFECYCLE" to BuildConfig.DEP_LIFECYCLE,
            "DEP_COREKTX" to BuildConfig.DEP_COREKTX,
            "DEP_SAVEDSTATE" to BuildConfig.DEP_SAVEDSTATE,
            "DEP_TRACING" to BuildConfig.DEP_TRACING,
            "DEP_KOTLIN" to BuildConfig.DEP_KOTLIN,
            "DEP_COROUTINES" to BuildConfig.DEP_COROUTINES
        )
        constants.forEach { (name, value) ->
            assertFalse("$name 未在 app/build.gradle 中赋值", value.isBlank())
        }
    }

    /** 格式化结果须逐个包含组件名与版本，且用统一分隔符，供界面单行展示。 */
    @Test
    fun formattedLineContainsEveryComponent() {
        val line = formatThirdPartyVersions()
        assertTrue("格式化结果不应包含换行", !line.contains('\n'))
        thirdPartyVersionList.forEach { (name, version) ->
            assertTrue("格式化结果缺少组件 $name", line.contains(name))
            assertTrue("格式化结果缺少 $name 的版本 $version", line.contains(version))
        }
        assertEquals(
            "分隔符应为 ' · '",
            thirdPartyVersionList.size - 1,
            Regex(" · ").findAll(line).count()
        )
    }
}
