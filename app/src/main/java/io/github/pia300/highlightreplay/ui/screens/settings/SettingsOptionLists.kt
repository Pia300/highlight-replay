package io.github.pia300.highlightreplay.ui.screens.settings

import android.content.Context
import android.os.Build
import io.github.pia300.highlightreplay.R
import io.github.pia300.highlightreplay.data.AudioSourceMode
import io.github.pia300.highlightreplay.data.CaptureOrientation
import io.github.pia300.highlightreplay.data.EncoderPreference
import io.github.pia300.highlightreplay.data.LanguagePrefs
import io.github.pia300.highlightreplay.data.RecorderSettings
import io.github.pia300.highlightreplay.data.ResolutionMode
import io.github.pia300.highlightreplay.data.ThemePrefs
import io.github.pia300.highlightreplay.data.VideoCodec
import io.github.pia300.highlightreplay.ui.SettingOptionLabels
import io.github.pia300.highlightreplay.ui.components.SettingChoice
import io.github.pia300.highlightreplay.ui.components.SettingIcons

/** 设置页各组的选项列表。 */
internal data class SettingsOptionLists(
    val codecOptions: List<SettingChoice>,
    val encoderOptions: List<SettingChoice>,
    val resolutionOptions: List<SettingChoice>,
    val resolutionModeOptions: List<SettingChoice>,
    val frameRateOptions: List<SettingChoice>,
    val bitrateOptions: List<SettingChoice>,
    val orientationOptions: List<SettingChoice>,
    val audioOptions: List<SettingChoice>,
    val replayDurationOptions: List<SettingChoice>,
    val darkModeOptions: List<SettingChoice>
)

/** 构建设置页选项列表；H.265 与系统内录按可用性裁剪。 */
internal fun settingsOptionLists(context: Context, hevcSupported: Boolean): SettingsOptionLists {
    fun str(resId: Int): String = LanguagePrefs.string(context, resId)

    val codecOptions = listOfNotNull(
        SettingChoice(VideoCodec.H264.prefValue, str(R.string.settings_codec_h264)),
        if (hevcSupported) {
            SettingChoice(VideoCodec.H265.prefValue, str(R.string.settings_codec_h265))
        } else {
            null
        }
    )

    val encoderOptions = listOf(
        SettingChoice(
            value = EncoderPreference.HARDWARE.prefValue,
            label = str(R.string.settings_encoder_hardware),
            icon = SettingIcons.encoderHardware
        ),
        SettingChoice(
            value = EncoderPreference.SOFTWARE.prefValue,
            label = str(R.string.settings_encoder_software),
            icon = SettingIcons.encoderSoftware
        ),
        SettingChoice(
            value = EncoderPreference.AUTO.prefValue,
            label = str(R.string.settings_encoder_auto),
            icon = SettingIcons.encoderAuto
        )
    )

    val resolutionOptions = RecorderSettings.RESOLUTION_OPTIONS.map { value ->
        SettingChoice(
            value = value.toString(),
            label = str(requireNotNull(SettingOptionLabels.resolution(value)))
        )
    }

    val resolutionModeOptions = listOf(
        SettingChoice(
            value = ResolutionMode.ADAPTIVE.prefValue,
            label = str(R.string.settings_resolution_mode_adaptive)
        ),
        SettingChoice(
            value = ResolutionMode.STANDARD.prefValue,
            label = str(R.string.settings_resolution_mode_standard)
        )
    )

    val frameRateOptions = RecorderSettings.FRAME_RATE_OPTIONS.map { value ->
        SettingChoice(
            value = value.toString(),
            label = str(requireNotNull(SettingOptionLabels.frameRate(value)))
        )
    }

    val bitrateOptions = RecorderSettings.BIT_RATE_OPTIONS.map { value ->
        SettingChoice(
            value = value.toString(),
            label = str(requireNotNull(SettingOptionLabels.bitRate(value)))
        )
    }

    val orientationOptions = listOf(
        SettingChoice(
            value = CaptureOrientation.AUTO.prefValue,
            label = str(R.string.settings_orientation_auto)
        ),
        SettingChoice(
            value = CaptureOrientation.PORTRAIT.prefValue,
            label = str(R.string.settings_orientation_portrait)
        ),
        SettingChoice(
            value = CaptureOrientation.LANDSCAPE.prefValue,
            label = str(R.string.settings_orientation_landscape)
        )
    )

    val audioOptions = listOfNotNull(
        SettingChoice(
            value = AudioSourceMode.NONE.prefValue,
            label = str(R.string.settings_audio_none)
        ),
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            SettingChoice(
                value = AudioSourceMode.INTERNAL.prefValue,
                label = str(R.string.settings_audio_internal)
            )
        } else {
            null
        }
    )

    val replayDurationOptions = RecorderSettings.REPLAY_DURATION_OPTIONS.map { value ->
        SettingChoice(
            value = value.toString(),
            label = str(requireNotNull(SettingOptionLabels.replayDuration(value)))
        )
    }

    val darkModeOptions = listOf(
        SettingChoice(
            value = ThemePrefs.MODE_SYSTEM,
            label = str(R.string.settings_dark_system),
            icon = SettingIcons.themeSystem
        ),
        SettingChoice(
            value = ThemePrefs.MODE_LIGHT,
            label = str(R.string.settings_dark_light),
            icon = SettingIcons.themeLight
        ),
        SettingChoice(
            value = ThemePrefs.MODE_DARK,
            label = str(R.string.settings_dark_dark),
            icon = SettingIcons.themeDark
        )
    )

    return SettingsOptionLists(
        codecOptions = codecOptions,
        encoderOptions = encoderOptions,
        resolutionOptions = resolutionOptions,
        resolutionModeOptions = resolutionModeOptions,
        frameRateOptions = frameRateOptions,
        bitrateOptions = bitrateOptions,
        orientationOptions = orientationOptions,
        audioOptions = audioOptions,
        replayDurationOptions = replayDurationOptions,
        darkModeOptions = darkModeOptions
    )
}
