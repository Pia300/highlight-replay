package io.github.pia300.highlightreplay.engine

import android.opengl.GLES11Ext
import android.opengl.GLES20

/** GL 基础工具：着色器程序构建与外部 OES 纹理创建；与 EGL 生命周期无关，可在任意 GL 上下文调用。 */
internal object GlUtils {

    /** 编译顶点与片元着色器并链接，返回程序 ID。 */
    fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragment = try {
            compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        } catch (e: Exception) {
            // 片元编译失败时顶点着色器尚未挂接程序，删除后重抛，避免 GL 对象泄漏。
            GLES20.glDeleteShader(vertex)
            throw e
        }
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vertex)
        GLES20.glAttachShader(prog, fragment)
        GLES20.glLinkProgram(prog)
        GLES20.glDeleteShader(vertex)
        GLES20.glDeleteShader(fragment)
        val status = IntArray(1)
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            // 链接失败：先 glDeleteProgram 再抛错，避免泄漏 GL 对象。
            val infoLog = GLES20.glGetProgramInfoLog(prog)
            GLES20.glDeleteProgram(prog)
            throw IllegalStateException("shader link failed: $infoLog")
        }
        return prog
    }

    /** 编译单个着色器并检查编译状态。 */
    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            // 编译失败：先释放 shader 再抛错（创建程序前抛出，无需额外清理）。
            val infoLog = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw IllegalStateException("shader compile failed: $infoLog")
        }
        return shader
    }

    /** 创建并配置外部 OES 纹理（SurfaceTexture 的采样来源）。 */
    fun createOesTexture(): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0])
        // 线性过滤并钳制到边缘，避免越界采样伪影。
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE
        )
        return textures[0]
    }
}
