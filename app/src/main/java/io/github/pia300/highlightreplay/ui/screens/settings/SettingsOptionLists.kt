package io.github.pia300.highlightreplay.ui.screens.settings

import android.content.Context
import android.os.Build
import io.github.pia300.highlightreplay.R
import io.github.pia300.highlightreplay.data.AudioSourceMode
import io.github.pia300.highlightreplay.data.EncoderPreference
import io.github.pia300.highlightreplay.data.LanguagePrefs
import io.github.pia300.highlightreplay.data.VideoCodec

/** 设置页三个单选选项列表：编码格式、编码器类型与音频来源。 */
internal data class SettingsOptionLists(
    val codecOptions: List<Pair<String, String>>,
    val encoderOptions: List<Pair<String, String>>,
    val audioOptions: List<Pair<String, String>>
)

/**
 * 构建设置页的编码格式、编码器类型与音频来源选项列表。
 * 编码格式的 H.265 选项仅在 [hevcSupported] 为 true 时提供，内录音频选项仅在 Android 10 及以上提供。
 *
 * 供 `remember` 调用，故不依赖 `stringResource`：字符串经语言偏好解析。
 */
internal fun settingsOptionLists(context: Context, hevcSupported: Boolean): SettingsOptionLists {
    fun str(resId: Int): String = LanguagePrefs.string(context, resId)
    val codecOptions = listOfNotNull(
        str(R.string.settings_codec_h264) to VideoCodec.H264.prefValue,
        if (hevcSupported) str(R.string.settings_codec_h265) to VideoCodec.H265.prefValue else null
    )
    // 编码器类型：硬件优先 / 仅软件 / 自动。软件编码器兼容尺寸更宽但 CPU 占用高，故保留手动选项。
    val encoderOptions = listOf(
        str(R.string.settings_encoder_hardware) to EncoderPreference.HARDWARE.prefValue,
        str(R.string.settings_encoder_software) to EncoderPreference.SOFTWARE.prefValue,
        str(R.string.settings_encoder_auto) to EncoderPreference.AUTO.prefValue
    )
    val audioOptions = listOfNotNull(
        str(R.string.settings_audio_none) to AudioSourceMode.NONE.prefValue,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            str(R.string.settings_audio_internal) to AudioSourceMode.INTERNAL.prefValue
        } else null
    )
    return SettingsOptionLists(codecOptions, encoderOptions, audioOptions)
}
