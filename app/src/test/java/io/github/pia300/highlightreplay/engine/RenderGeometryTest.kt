package io.github.pia300.highlightreplay.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** 渲染几何计算的单元测试。 */
class RenderGeometryTest {

    /** 浮点数组比较的允许误差。 */
    private val delta = 1e-6f

    /** 返回指定旋转与输入/输出尺寸下的纹理坐标。 */
    private fun textureCoordinatesOf(
        rotationDegrees: Int,
        inputWidth: Int,
        inputHeight: Int,
        outputWidth: Int,
        outputHeight: Int
    ): FloatArray =
        buildRenderGeometry(
            rotationDegrees, inputWidth, inputHeight, outputWidth, outputHeight
        ).textureCoordinates

    /** 验证 0° 旋转生成与传统布局完全一致的纹理坐标。 */
    @Test
    fun identityWithoutRotationMatchesLegacyLayout() {

        val coordinates = textureCoordinatesOf(0, 1920, 1080, 1920, 1080)
        assertArrayEquals(
            floatArrayOf(
                0f, 1f, 1f, 1f,
                1f, 1f, 1f, 1f,
                0f, 0f, 1f, 1f,
                1f, 0f, 1f, 1f
            ), coordinates, delta
        )
    }

    /** 验证 90° 旋转交换纹理坐标的 u/v 角度。 */
    @Test
    fun rotate90SwapsAngles() {

        val coordinates = textureCoordinatesOf(90, 1000, 1000, 1000, 1000)
        assertArrayEquals(
            floatArrayOf(
                1f, 1f, 1f, 1f,
                1f, 0f, 1f, 1f,
                0f, 1f, 1f, 1f,
                0f, 0f, 1f, 1f
            ), coordinates, delta
        )
    }

    /** 验证宽高互换且分辨率匹配时 90° 旋转恰好适配，缩放比例均为 1。 */
    @Test
    fun rotate90ExactFitForSwappedAspect() {

        val g = buildRenderGeometry(90, 720, 1280, 1280, 720)
        assertArrayEquals(
            floatArrayOf(
                1f, 1f, 1f, 1f,
                1f, 0f, 1f, 1f,
                0f, 1f, 1f, 1f,
                0f, 0f, 1f, 1f
            ), g.textureCoordinates, delta
        )
        assertEquals(1f, g.quadScaleX, delta)
        assertEquals(1f, g.quadScaleY, delta)
    }

    /** 验证内容与输出宽高比不一致时产生信箱式（letterbox）缩放。 */
    @Test
    fun letterboxWhenAspectMismatch() {

        val g = buildRenderGeometry(90, 1080, 2400, 1920, 1080)

        assertEquals(1f, g.quadScaleX, delta)
        assertEquals(0.8f, g.quadScaleY, delta)

        assertArrayEquals(
            floatArrayOf(
                1f, 1f, 1f, 1f,
                1f, 0f, 1f, 1f,
                0f, 1f, 1f, 1f,
                0f, 0f, 1f, 1f
            ), g.textureCoordinates, delta
        )
    }

    /** 验证内容比输出更高时沿宽度方向进行信箱式缩放。 */
    @Test
    fun letterboxWidthAxisWhenContentTaller() {

        val g = buildRenderGeometry(0, 1080, 2400, 1080, 1920)
        assertEquals(0.8f, g.quadScaleX, delta)
        assertEquals(1f, g.quadScaleY, delta)

        assertArrayEquals(
            floatArrayOf(
                0f, 1f, 1f, 1f,
                1f, 1f, 1f, 1f,
                0f, 0f, 1f, 1f,
                1f, 0f, 1f, 1f
            ), g.textureCoordinates, delta
        )
    }

    /** 验证宽高比一致时不产生信箱式缩放。 */
    @Test
    fun noLetterboxWhenAspectsMatch() {

        val g1 = buildRenderGeometry(90, 1080, 1920, 1920, 1080)
        assertEquals(1f, g1.quadScaleX, delta)
        assertEquals(1f, g1.quadScaleY, delta)

        val g2 = buildRenderGeometry(0, 2400, 1080, 2400, 1080)
        assertEquals(1f, g2.quadScaleX, delta)
        assertEquals(1f, g2.quadScaleY, delta)
    }

    /** 验证 180°、270°、45° 等不支持的角度抛出 IllegalArgumentException。 */
    @Test
    fun unsupportedRotationsRejected() {

        assertThrows(IllegalArgumentException::class.java) {
            buildRenderGeometry(180, 1000, 1000, 1000, 1000)
        }
        assertThrows(IllegalArgumentException::class.java) {
            buildRenderGeometry(270, 1000, 1000, 1000, 1000)
        }
        assertThrows(IllegalArgumentException::class.java) {
            buildRenderGeometry(45, 1000, 1000, 1000, 1000)
        }
    }
}
