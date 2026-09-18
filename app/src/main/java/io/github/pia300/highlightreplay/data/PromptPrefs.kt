package io.github.pia300.highlightreplay.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** 引导标记偏好文件名；备份规则按「文件名.xml」排除本文件。 */
const val PROMPT_PREFS_NAME = "prompt_state"

/** 通知权限是否已引导过（含被拒）。 */
const val KEY_NOTIFICATION_PROMPTED = "notification_prompted"

/** 录音权限是否已被拒过。 */
const val KEY_AUDIO_PROMPTED = "audio_prompted"

/** 电池优化豁免是否已引导过。 */
const val KEY_BATTERY_PROMPTED = "battery_prompted"

/** 悬浮窗授权页是否已自动引导过。 */
const val KEY_OVERLAY_PROMPTED = "overlay_prompted"

/** 旧标记搬运完成标记。 */
private const val KEY_LEGACY_CARRIED = "legacy_flags_carried"

/** 曾存放在主偏好文件中的引导标记键。 */
private val LEGACY_PROMPT_KEYS = listOf(
    KEY_NOTIFICATION_PROMPTED, KEY_AUDIO_PROMPTED, KEY_BATTERY_PROMPTED, KEY_OVERLAY_PROMPTED
)

/**
 * 一次性引导标记的偏好文件：记录本机是否已引导/被拒过某项权限，与用户设置分开存放。
 *
 * 这些标记描述的是「本机」状态，它们门控的权限不随备份恢复，故随备份或换机迁移会使
 * 新设备跳过引导，而权限仍未授予。backup_rules.xml 与 data_extraction_rules.xml
 * 按 [PROMPT_PREFS_NAME] 排除本文件；用户设置仍随主偏好文件迁移。
 *
 * 首次取用时把 [LEGACY_PROMPT_KEYS] 从主偏好文件搬运过来，使同机升级不重复引导；
 * 搬运后从主偏好文件移除，避免主偏好文件随备份迁移时被再次导入。
 */
fun Context.promptPrefs(): SharedPreferences {
    val prefs = getSharedPreferences(PROMPT_PREFS_NAME, Context.MODE_PRIVATE)
    if (!prefs.getBoolean(KEY_LEGACY_CARRIED, false)) {
        val legacy = defaultPrefs()
        val carried = LEGACY_PROMPT_KEYS.filter { legacy.contains(it) }
        prefs.edit {
            putBoolean(KEY_LEGACY_CARRIED, true)
            carried.forEach { putBoolean(it, legacy.getBoolean(it, false)) }
        }
        if (carried.isNotEmpty()) {
            legacy.edit { carried.forEach { remove(it) } }
        }
    }
    return prefs
}
