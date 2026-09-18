package io.github.pia300.highlightreplay.engine

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.view.Surface

/**
 * 编码器渲染目标上的 EGL 显示、上下文与窗口表面：创建、绑定、提交与释放。
 *
 * @param encoderSurface EGL 窗口表面的渲染目标（编码器输入 Surface）。
 */
internal class EglCore(private val encoderSurface: Surface) {

    /** EGL 显示连接，EGL_NO_DISPLAY 表示未初始化。 */
    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    /** EGL 渲染上下文，EGL_NO_CONTEXT 表示未初始化。 */
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    /** EGL 窗口表面，渲染目标为编码器 Surface。 */
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    /** 初始化 EGL：获取显示、选择可录制配置、创建上下文与编码器窗口表面。 */
    fun init() {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != EGL14.EGL_NO_DISPLAY) { "eglGetDisplay failed" }
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "eglInitialize failed" }
        eglDisplay = display

        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        check(
            EGL14.eglChooseConfig(
                display,
                // 请求 8 位 RGBA、OpenGL ES 2 且可被 MediaCodec 录制的配置。
                intArrayOf(
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGLExt.EGL_RECORDABLE_ANDROID, 1,
                    EGL14.EGL_NONE
                ), 0,
                configs, 0, 1,
                numConfigs, 0
            )
        ) { "eglChooseConfig failed" }
        check(numConfigs[0] > 0 && configs[0] != null) { "no recordable EGL config" }
        val config = configs[0]!!

        val context = EGL14.eglCreateContext(
            display, config, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0
        )
        check(context != EGL14.EGL_NO_CONTEXT) { "eglCreateContext failed" }
        eglContext = context

        val surface = EGL14.eglCreateWindowSurface(
            display, config, encoderSurface, intArrayOf(EGL14.EGL_NONE), 0
        )
        check(surface != EGL14.EGL_NO_SURFACE) { "eglCreateWindowSurface failed" }
        eglSurface = surface
    }

    /** 绑定 EGL 上下文与窗口表面；返回是否绑定成功。 */
    fun makeCurrent(): Boolean =
        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)

    /** 解除当前 EGL 绑定（无表面、无上下文）。 */
    fun releaseCurrent() {
        EGL14.eglMakeCurrent(
            eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT
        )
    }

    /** 指定呈现时间戳并交换缓冲；返回交换是否成功。 */
    fun presentAndSwap(presentationTimeNs: Long): Boolean {
        EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, presentationTimeNs)
        return EGL14.eglSwapBuffers(eglDisplay, eglSurface)
    }

    /** 释放 EGL 表面、上下文与显示连接（幂等）。 */
    fun teardown() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            try {
                // 先解除绑定再销毁表面与上下文；顺序不可颠倒。
                EGL14.eglMakeCurrent(
                    eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT
                )
            } catch (_: Exception) {
            }
            if (eglSurface != EGL14.EGL_NO_SURFACE) {
                try {
                    EGL14.eglDestroySurface(eglDisplay, eglSurface)
                } catch (_: Exception) {
                }
                eglSurface = EGL14.EGL_NO_SURFACE
            }
            if (eglContext != EGL14.EGL_NO_CONTEXT) {
                try {
                    EGL14.eglDestroyContext(eglDisplay, eglContext)
                } catch (_: Exception) {
                }
                eglContext = EGL14.EGL_NO_CONTEXT
            }
            try {
                EGL14.eglTerminate(eglDisplay)
            } catch (_: Exception) {
            }
            eglDisplay = EGL14.EGL_NO_DISPLAY
        }
    }
}
