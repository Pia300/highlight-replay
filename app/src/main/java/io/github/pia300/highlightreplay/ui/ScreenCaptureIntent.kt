package io.github.pia300.highlightreplay.ui

import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build

/**
 * 屏幕捕获授权 Intent 的**唯一构造点**，两条入口共用：应用内「开始录制」
 * （[RecordingStartFlow]）与磁贴冷启动（[io.github.pia300.highlightreplay.RecordingStartActivity]）。
 *
 * Android 14 起授权框可选「共享一个应用」与「共享整个屏幕」，而按应用共享的镜像区域
 * 在被选应用退到后台后为空，录出的整段画面会是纯黑且不报错。此处显式请求整屏捕获，
 * 使授权框只提供「共享整个屏幕」。
 *
 * [MediaProjectionConfig.createConfigForDefaultDisplay] 需 API 34；更早版本无按应用共享，
 * 返回默认 Intent。
 *
 * 调用点唯一性由构建任务 `checkCaptureIntentConstruction` 守：除此文件外，任何地方
 * 以 `.createScreenCaptureIntent(` 形态调用都会让构建失败。新增授权入口必须调用本函数，
 * 否则两条入口会请求不同的捕获范围。
 */
internal fun MediaProjectionManager.newScreenCaptureIntent(): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
    } else {
        createScreenCaptureIntent()
    }
