package io.github.pia300.highlightreplay

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import io.github.pia300.highlightreplay.data.RecorderSettings

/** 收集启动录制尚缺的运行时权限：录音（启用音频时）、通知（13+）、旧版存储（API≤28）。 */
internal fun collectMissingRuntimePermissions(context: Context): List<String> {
    val permissions = mutableListOf<String>()
    val settings = RecorderSettings.fromPreferences(context)
    if (settings.hasAudio() &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        permissions.add(Manifest.permission.RECORD_AUDIO)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
    }
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    return permissions
}

/** 历史回录所需的媒体读取权限：Android 13+ 使用 READ_MEDIA_VIDEO，之前使用 READ_EXTERNAL_STORAGE。 */
internal fun requiredMediaReadPermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

/** 当前是否已获得历史回录所需的媒体读取权限（含 Android 14+ 部分访问）。 */
internal fun hasMediaReadPermission(context: Context): Boolean {
    if (ContextCompat.checkSelfPermission(context, requiredMediaReadPermission()) ==
        PackageManager.PERMISSION_GRANTED
    ) {
        return true
    }
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        ) == PackageManager.PERMISSION_GRANTED
}
