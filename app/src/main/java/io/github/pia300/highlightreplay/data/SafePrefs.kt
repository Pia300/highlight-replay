package io.github.pia300.highlightreplay.data

import android.content.SharedPreferences

/**
 * 类型安全的偏好读取。
 *
 * SharedPreferences 的强类型读取在类型不符时会抛 ClassCastException（键被不同类型写入、
 * 版本降级或备份恢复都可能造成），而读取点大多在启动路径上，崩溃即无法进入应用。
 * 这里把“类型不符”降级为“未设置”，由调用方回退默认值。
 */

/** 读取字符串偏好；键存有其他类型时返回 [default]。 */
internal fun SharedPreferences.getStringSafe(key: String, default: String? = null): String? =
    try {
        getString(key, default)
    } catch (_: ClassCastException) {
        default
    }

/** 读取整型偏好；键存有其他类型时返回 [default]。 */
internal fun SharedPreferences.getIntSafe(key: String, default: Int): Int =
    try {
        getInt(key, default)
    } catch (_: ClassCastException) {
        default
    }

/** 读取布尔偏好；键存有其他类型时返回 [default]。 */
internal fun SharedPreferences.getBooleanSafe(key: String, default: Boolean): Boolean =
    try {
        getBoolean(key, default)
    } catch (_: ClassCastException) {
        default
    }
