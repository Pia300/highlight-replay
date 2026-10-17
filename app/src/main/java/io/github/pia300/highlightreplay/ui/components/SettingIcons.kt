package io.github.pia300.highlightreplay.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material.icons.outlined.Opacity
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Rotate90DegreesCcw
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.ui.graphics.vector.ImageVector

/** 设置页使用的图标集合。 */
object SettingIcons {
    val video: ImageVector = Icons.Outlined.Videocam
    val codec: ImageVector = Icons.Outlined.Movie
    val encoder: ImageVector = Icons.Outlined.Memory
    val resolution: ImageVector = Icons.Outlined.AspectRatio
    val resolutionMode: ImageVector = Icons.Outlined.FitScreen
    val frameRate: ImageVector = Icons.Outlined.Speed
    val bitrate: ImageVector = Icons.Outlined.DataUsage
    val orientation: ImageVector = Icons.Outlined.ScreenRotation
    val contentRotation: ImageVector = Icons.Outlined.Rotate90DegreesCcw

    val audio: ImageVector = Icons.Outlined.GraphicEq
    val audioSource: ImageVector = Icons.Outlined.Mic
    val replay: ImageVector = Icons.Outlined.Replay
    val replayDuration: ImageVector = Icons.Outlined.Schedule

    val general: ImageVector = Icons.Outlined.Tune
    val toast: ImageVector = Icons.Outlined.NotificationsActive
    val audioMonitor: ImageVector = Icons.Outlined.Hearing
    val shake: ImageVector = Icons.Outlined.Vibration

    val floating: ImageVector = Icons.Outlined.PictureInPictureAlt
    val floatingVisibility: ImageVector = Icons.Outlined.Visibility
    val floatingSize: ImageVector = Icons.Outlined.OpenWith
    val floatingOpacity: ImageVector = Icons.Outlined.Opacity
    val reset: ImageVector = Icons.Outlined.RestartAlt

    val theme: ImageVector = Icons.Outlined.Palette
    val darkMode: ImageVector = Icons.Outlined.Contrast
    val themeColor: ImageVector = Icons.Outlined.ColorLens
    val themeSystem: ImageVector = Icons.Outlined.BrightnessAuto
    val themeLight: ImageVector = Icons.Outlined.LightMode
    val themeDark: ImageVector = Icons.Outlined.DarkMode

    val language: ImageVector = Icons.Outlined.Translate
    val permission: ImageVector = Icons.Outlined.VerifiedUser
    val overlayPermission: ImageVector = Icons.Outlined.Layers
    val notificationPermission: ImageVector = Icons.Outlined.Notifications
    val batteryPermission: ImageVector = Icons.Outlined.BatterySaver
    val storagePermission: ImageVector = Icons.Outlined.Folder
    val granted: ImageVector = Icons.Outlined.CheckCircle
    val denied: ImageVector = Icons.Outlined.ErrorOutline

    val about: ImageVector = Icons.Outlined.Info
    val license: ImageVector = Icons.Outlined.Description
    val project: ImageVector = Icons.Outlined.Public

    val encoderHardware: ImageVector = Icons.Outlined.Memory
    val encoderSoftware: ImageVector = Icons.Outlined.Code
    val encoderAuto: ImageVector = Icons.Outlined.AutoAwesome

}
