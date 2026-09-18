package io.github.pia300.highlightreplay.ui

import androidx.annotation.StringRes
import io.github.pia300.highlightreplay.R

/** 录制设置数值档位到文案资源的映射：设置页选项列表与录制设置摘要卡片共用，避免两处分别维护。 */
object SettingOptionLabels {

    /** 分辨率档位文案；未收录的数值返回 null。 */
    @StringRes
    fun resolution(value: Int): Int? = when (value) {
        720 -> R.string.settings_resolution_720p
        1080 -> R.string.settings_resolution_1080p
        1440 -> R.string.settings_resolution_1440p
        else -> null
    }

    /** 帧率档位文案；未收录的数值返回 null。 */
    @StringRes
    fun frameRate(value: Int): Int? = when (value) {
        24 -> R.string.settings_fps_24
        30 -> R.string.settings_fps_30
        60 -> R.string.settings_fps_60
        else -> null
    }

    /** 码率档位文案；未收录的数值返回 null。 */
    @StringRes
    fun bitRate(value: Int): Int? = when (value) {
        4 -> R.string.settings_bitrate_4
        8 -> R.string.settings_bitrate_8
        12 -> R.string.settings_bitrate_12
        16 -> R.string.settings_bitrate_16
        else -> null
    }

    /** 回放时长档位文案；未收录的数值返回 null。 */
    @StringRes
    fun replayDuration(value: Int): Int? = when (value) {
        30 -> R.string.settings_replay_30s
        60 -> R.string.settings_replay_60s
        else -> null
    }
}
