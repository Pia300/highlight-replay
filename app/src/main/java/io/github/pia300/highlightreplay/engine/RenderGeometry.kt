package io.github.pia300.highlightreplay.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/** 单次绘制所需四边形几何：纹理坐标与信箱缩放。 */
class RenderGeometry(
    /** 纹理坐标数组，每顶点 4 分量（u、v、1、1），z 为恒等分量。 */
    val textureCoordinates: FloatArray,
    /** 四边形 X 方向缩放（信箱黑边适配）。 */
    val quadScaleX: Float,
    /** 四边形 Y 方向缩放（信箱黑边适配）。 */
    val quadScaleY: Float
)

/** 自适应模式的输入状态：旋转角与输入宽高。 */
data class AdaptState(

    val rotationDegrees: Int,
    val inputWidth: Int,
    val inputHeight: Int
)

/** 覆盖整个视口的三角形带顶点（x、y、z），z 恒为 1。 */
private val QUAD_POSITIONS = floatArrayOf(
    -1f, -1f, 1f,
    1f, -1f, 1f,
    -1f, 1f, 1f,
    1f, 1f, 1f
)

/** 默认四边形纹理坐标，每顶点 4 分量（u、v、1、1），z 恒等分量，供片元透视除法。 */
private val QUAD_TEX_COORDINATES = floatArrayOf(
    0f, 1f, 1f, 1f,
    1f, 1f, 1f, 1f,
    0f, 0f, 1f, 1f,
    1f, 0f, 1f, 1f
)

/** 根据旋转与输入/输出尺寸计算绘制几何（纹理坐标与信箱缩放）。 */
internal fun buildRenderGeometry(
    rotationDegrees: Int,
    inputWidth: Int,
    inputHeight: Int,
    outputWidth: Int,
    outputHeight: Int
): RenderGeometry {
    // 归一化到 [0, 360)，使超范围与负数输入统一进入下方校验。
    val rot = ((rotationDegrees % 360) + 360) % 360

    require(rot == 0 || rot == 90) { "unsupported rotation: $rotationDegrees (only 0/90)" }
    val rotatedW = if (rot == 90) inputHeight else inputWidth
    val rotatedH = if (rot == 90) inputWidth else inputHeight
    // 由帧与内容宽高比推导信箱缩放，保证内容完整可见。
    val frameAspect = outputWidth.toFloat() / outputHeight
    val contentAspect = rotatedW.toFloat() / rotatedH

    val quadScaleX = minOf(1f, contentAspect / frameAspect)
    val quadScaleY = minOf(1f, frameAspect / contentAspect)
    // 按旋转重排纹理坐标，保证画面方向正确。
    val out = FloatArray(16)
    for (i in 0 until 4) {
        val u0 = QUAD_TEX_COORDINATES[i * 4]
        val v0 = QUAD_TEX_COORDINATES[i * 4 + 1]
        val u: Float
        val v: Float
        when (rot) {
            90 -> {
                u = v0; v = 1f - u0
            }

            else -> {
                u = u0; v = v0
            }
        }

        out[i * 4] = u
        out[i * 4 + 1] = v
        out[i * 4 + 2] = 1f
        out[i * 4 + 3] = 1f
    }
    return RenderGeometry(out, quadScaleX, quadScaleY)
}

/** 按信箱缩放系数缩放基础四边形顶点。 */
internal fun scaledQuad(sx: Float, sy: Float): FloatArray {
    val scaled = FloatArray(QUAD_POSITIONS.size)
    for (i in QUAD_POSITIONS.indices) {
        scaled[i] = when (i % 3) {
            0 -> QUAD_POSITIONS[i] * sx
            1 -> QUAD_POSITIONS[i] * sy
            else -> QUAD_POSITIONS[i]
        }
    }
    return scaled
}

/** 在 native 内存创建直接 FloatBuffer（GL 要求直接缓冲）。 */
internal fun directFloatBuffer(values: FloatArray): FloatBuffer =
    ByteBuffer.allocateDirect(values.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(values)
            position(0)
        }
