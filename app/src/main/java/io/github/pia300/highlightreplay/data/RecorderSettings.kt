package io.github.pia300.highlightreplay.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build

/** 应用主偏好文件（所有设置与悬浮球偏好的统一存放处）。 */
fun Context.defaultPrefs(): SharedPreferences =
    getSharedPreferences(packageName + "_preferences", Context.MODE_PRIVATE)

/** 编码器选项：H.264 兼容性最好、H.265 压缩率更高；无自动回退，H.265 启动失败即中止。 */
enum class VideoCodec(val prefValue: String, val mimeType: String) {
    H264("h264", "video/avc"),
    H265("h265", "video/hevc");

    companion object {
        /** 解析存储值，非法时回退 H.264。 */
        fun fromPref(value: String?): VideoCodec =
            entries.firstOrNull { it.prefValue == value } ?: H264
    }
}

/**
 * 编码器类型偏好：硬件 / 软件 / 自动。
 *
 * 硬件编码器省电、发热低，但个别机型对分辨率/帧率上限更严；软件编码器支持尺寸更宽，
 * 但 CPU 占用高。[AUTO] 由系统选定编码器并按其能力钳制尺寸，作为兜底选项。
 */
enum class EncoderPreference(val prefValue: String) {
    HARDWARE("hardware"),
    SOFTWARE("software"),
    AUTO("auto");

    companion object {
        /** 解析存储值，非法时回退 [AUTO]。 */
        fun fromPref(value: String?): EncoderPreference =
            entries.firstOrNull { it.prefValue == value } ?: AUTO
    }
}

/** 音频来源：无音频 / 仅系统内录。 */
enum class AudioSourceMode(val prefValue: String) {
    NONE("none"),
    INTERNAL("internal");

    companion object {
        fun fromPref(value: String?): AudioSourceMode =
            entries.firstOrNull { it.prefValue == value } ?: NONE
    }
}

/** 录制方向：自动（跟随会话开始时的屏幕方向）/ 强制竖屏 / 强制横屏。 */
enum class CaptureOrientation(val prefValue: String) {
    AUTO("auto"),
    PORTRAIT("portrait"),
    LANDSCAPE("landscape");

    companion object {
        fun fromPref(value: String?): CaptureOrientation =
            entries.firstOrNull { it.prefValue == value } ?: AUTO
    }
}

/** 分辨率模式：自适应（跟随屏幕宽高比）/ 标准（固定 16:9）。 */
enum class ResolutionMode(val prefValue: String) {
    ADAPTIVE("adaptive"),
    STANDARD("standard");

    companion object {
        fun fromPref(value: String?): ResolutionMode =
            entries.firstOrNull { it.prefValue == value } ?: STANDARD
    }
}

/** 全部录制设置；字段以字符串存储（枚举存枚举值、数值存字符串），由 [fromPreferences] 统一解析。 */
data class RecorderSettings(
    val resolution: Int = 1080,
    val resolutionMode: String = ResolutionMode.STANDARD.prefValue,
    val frameRate: Int = 30,
    val bitRate: Int = 8,
    val audioSource: String = defaultAudioSource(),
    val replayDuration: Int = 30,
    val orientation: String = CaptureOrientation.AUTO.prefValue,

    val codec: String = VideoCodec.H264.prefValue,
    // 编码器类型偏好：默认自动（由系统选定编码器，不做硬件筛选）。
    val encoderPreference: String = EncoderPreference.AUTO.prefValue,
    val toastNotify: String = VALUE_ON,
    // 内容自适应旋转默认开启：物理方向与录制方向不一致时内容自动对齐。
    val contentRotation: String = VALUE_ON,
    // 音频监视器默认关闭：关闭时不做音频电平判定，悬浮球与通知栏只指示视频状态。
    val audioMonitor: String = VALUE_OFF,
    // 摇一摇保存默认关闭：开启后才注册加速度计监听（避免默认占用传感器）。
    val shakeToSave: String = VALUE_OFF,
    // 触发力度（0..100）：数值越大，判定阈值越高，需要越用力摇动才会触发。
    val shakeStrength: Int = SHAKE_STRENGTH_DEFAULT
) {

    init {
        require(resolution in RESOLUTION_RANGE) { "resolution out of range: $resolution" }
        require(frameRate in FRAME_RATE_RANGE) { "frameRate out of range: $frameRate" }
        require(bitRate in BIT_RATE_RANGE) { "bitRate out of range: $bitRate" }
        require(replayDuration in REPLAY_DURATION_RANGE) { "replayDuration out of range: $replayDuration" }
        // 枚举字段一律"存的是合法 prefValue"。这条不变式由 fromPreferences 的归一化保证，
        // 这里再断言一次，使新增构造路径无法把非法值塞进来。
        // 只查非空是不够的：下游（VideoSizeCalculator.compute / orient）用穷尽 when 消费这些枚举，
        // 非法值不会崩，而是**静默退回 AUTO**——那是比崩溃更难查的失败。
        require(codec in VideoCodec.entries.map { it.prefValue }.toSet()) {
            "invalid codec: $codec"
        }
        require(orientation in CaptureOrientation.entries.map { it.prefValue }.toSet()) {
            "invalid orientation: $orientation"
        }
        require(resolutionMode in ResolutionMode.entries.map { it.prefValue }.toSet()) {
            "invalid resolutionMode: $resolutionMode"
        }
        require(audioSource in AudioSourceMode.entries.map { it.prefValue }.toSet()) {
            "invalid audioSource: $audioSource"
        }
        require(encoderPreference in ENCODER_PREFERENCE_VALUES) {
            "invalid encoderPreference: $encoderPreference"
        }
        require(toastNotify == VALUE_ON || toastNotify == VALUE_OFF) { "invalid toastNotify: $toastNotify" }
        require(contentRotation == VALUE_ON || contentRotation == VALUE_OFF) { "invalid contentRotation: $contentRotation" }
        require(audioMonitor == VALUE_ON || audioMonitor == VALUE_OFF) { "invalid audioMonitor: $audioMonitor" }
        require(shakeToSave == VALUE_ON || shakeToSave == VALUE_OFF) { "invalid shakeToSave: $shakeToSave" }
        require(shakeStrength in SHAKE_STRENGTH_RANGE) { "shakeStrength out of range: $shakeStrength" }
    }

    val codecEnum: VideoCodec get() = VideoCodec.fromPref(codec)
    val encoderPreferenceEnum: EncoderPreference get() = EncoderPreference.fromPref(encoderPreference)
    val audioMode: AudioSourceMode get() = AudioSourceMode.fromPref(audioSource)
    val captureOrientation: CaptureOrientation get() = CaptureOrientation.fromPref(orientation)
    val resolutionModeEnum: ResolutionMode get() = ResolutionMode.fromPref(resolutionMode)
    val contentRotationEnabled: Boolean get() = contentRotation == VALUE_ON

    /** 音频监视器是否开启：开启时才判定音频电平，供悬浮球与通知栏指示。 */
    val audioMonitorEnabled: Boolean get() = audioMonitor == VALUE_ON

    /** 摇一摇保存是否开启：开启后才注册加速度计监听。 */
    val shakeToSaveEnabled: Boolean get() = shakeToSave == VALUE_ON

    /** 视频码率（bps）；bitRate 字段以 Mbps 存储。 */
    fun getVideoBitRateBps(): Int = bitRate * 1_000_000

    /** 视频 MIME 类型，供 MediaCodec 使用。 */
    fun getVideoMimeType(): String = codecEnum.mimeType

    /** 音频环形缓冲块容量：每秒块数 × (回放时长 + 裕量) + 舍入余量。 */
    fun getAudioBufferCapacity(): Int {
        val blocksPerSecond = AUDIO_BYTES_PER_SECOND / AUDIO_BLOCK_SIZE
        return (replayDuration + BUFFER_MARGIN_SECONDS) * blocksPerSecond + AUDIO_BUFFER_CAPACITY_HEADROOM
    }

    /** 回放时长（毫秒）。 */
    fun getReplayDurationMs(): Long = replayDuration * 1000L

    /** 是否启用音频采集（内录）。 */
    fun hasAudio(): Boolean = audioMode != AudioSourceMode.NONE

    companion object {
        const val VALUE_ON = "1"
        const val VALUE_OFF = "0"

        const val KEY_CODEC = "codec"
        const val KEY_ENCODER_PREFERENCE = "encoder_preference"
        const val KEY_RESOLUTION = "resolution"
        const val KEY_RESOLUTION_MODE = "resolution_mode"
        const val KEY_FRAME_RATE = "frame_rate"
        const val KEY_BITRATE = "bitrate"
        const val KEY_ORIENTATION = "orientation"
        const val KEY_AUDIO_SOURCE = "audio_source"
        const val KEY_REPLAY_DURATION = "replay_duration"

        const val KEY_TOAST_NOTIFY = "toast_notify"
        const val KEY_CONTENT_ROTATION = "content_rotation"
        const val KEY_AUDIO_MONITOR = "audio_monitor"
        const val KEY_SHAKE_TO_SAVE = "shake_to_save"
        const val KEY_SHAKE_STRENGTH = "shake_strength"

        // 引擎物理范围（构造校验/缓冲容量钳制使用；比 UI 档位宽）。
        val RESOLUTION_RANGE = 480..4320
        val FRAME_RATE_RANGE = 15..144
        val BIT_RATE_RANGE = 1..100
        val REPLAY_DURATION_RANGE = 1..300

        // 触发力度：0（最容易触发）..100（最用力才触发）。范围即引擎换算阈值的定义域，
        // 故不是 UI 档位而是硬边界——越界值一律钳制，见 parseShakeStrength。
        val SHAKE_STRENGTH_RANGE = 0..100
        const val SHAKE_STRENGTH_DEFAULT = 50

        // 设置页 UI 档位；存储值不在档位内时吸附到最近档位。
        val RESOLUTION_OPTIONS = intArrayOf(720, 1080, 1440)
        val FRAME_RATE_OPTIONS = intArrayOf(24, 30, 60)
        val BIT_RATE_OPTIONS = intArrayOf(4, 8, 12, 16)
        /** 回放时长 UI 档位（秒）。 */
        val REPLAY_DURATION_OPTIONS = intArrayOf(30, 60)

        /** 编码器类型合法取值集合（构造校验用）。 */
        val ENCODER_PREFERENCE_VALUES = EncoderPreference.entries.map { it.prefValue }.toSet()

        // 缓冲裕量（秒）：覆盖编码器启动抖动与帧率估算误差。
        const val BUFFER_MARGIN_SECONDS = 2

        /** 请求关键帧的软上限（毫秒）：期望关键帧间隔 1 秒，开放分片超过该长度即请求补发。 */
        const val SOFT_SEGMENT_MS = 1600L

        /** 强制切段的硬上限（毫秒）：编码器始终不补发关键帧时的兜底，使单分片长度有界。 */
        const val HARD_SEGMENT_MS = 4000L

        /** 软上限回调的最小间隔（毫秒）：软上限逐帧求值，需节流避免重复请求。 */
        const val KEYFRAME_REQUEST_THROTTLE_MS = 1000L

        // 缓冲内存硬帽：独立于覆盖时长与帧率的最后一道防线，正常配置下不参与逐出决策。
        // 分片缓冲按覆盖时长逐出（时长即用户所选回放时长，不再按帧率估算），
        // 故本帽不承担"修正容量估算误差"的职责；它只在编码器行为异常、
        // 或码率设置被外部改动时兜底，防止内存失控。
        const val MAX_VIDEO_BUFFER_BYTES = 192L * 1024 * 1024
        const val MAX_AUDIO_BUFFER_BYTES = 16L * 1024 * 1024

        // PCM 参数：44.1kHz / 双声道 / 16bit。
        const val AUDIO_SAMPLE_RATE = 44100
        const val AUDIO_CHANNEL_COUNT = 2
        const val AUDIO_BYTES_PER_SAMPLE = 2
        const val AUDIO_BLOCK_SIZE = 4096
        const val AUDIO_BYTES_PER_SECOND =
            AUDIO_SAMPLE_RATE * AUDIO_CHANNEL_COUNT * AUDIO_BYTES_PER_SAMPLE

        // 音频缓冲块数的额外舍入余量。
        private const val AUDIO_BUFFER_CAPACITY_HEADROOM = 64

        /** 回放文件在 Movies 目录下的子目录名（MediaStore 相对路径与旧版文件路径共用）。 */
        const val SAVE_SUBDIR_NAME = "Highlight Replay"

        /** 默认音频来源：Android 10+ 为系统内录，否则无音频。 */
        fun defaultAudioSource(): String =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                AudioSourceMode.INTERNAL.prefValue
            } else {
                AudioSourceMode.NONE.prefValue
            }

        /** 解析音频来源：Android 10 以下强制无音频（系统内录不可用）。 */
        fun resolveAudioSource(prefs: SharedPreferences): String {
            val stored = prefs.getStringSafe(KEY_AUDIO_SOURCE)
                ?.let { AudioSourceMode.fromPref(it).prefValue }
                ?: defaultAudioSource()
            return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                stored == AudioSourceMode.INTERNAL.prefValue
            ) {
                AudioSourceMode.NONE.prefValue
            } else {
                stored
            }
        }

        /**
         * 解析触发力度：越界值钳制到 [SHAKE_STRENGTH_RANGE]，缺失/非数字回退默认值。
         *
         * 与 [snapToOption] 不同：力度是连续量（滑杆），不存在"吸附到档位"，只有边界与默认值。
         * （internal 供单元测试直接调用。）
         */
        internal fun parseShakeStrength(raw: String?): Int =
            raw?.toIntOrNull()?.coerceIn(SHAKE_STRENGTH_RANGE) ?: SHAKE_STRENGTH_DEFAULT

        /** 把存储值吸附到最近的 UI 档位；缺失/非法时回退默认档位（internal 供单元测试直接调用）。 */
        internal fun snapToOption(raw: String?, options: IntArray, default: Int): Int {
            val v = raw?.toIntOrNull() ?: return default
            return options.minByOrNull { kotlin.math.abs(it - v) } ?: default
        }

        /** 从偏好读取全部设置：数值字段按 UI 档位吸附；枚举字段解析失败回退默认；类型不符按未设置处理。 */
        fun fromPreferences(context: Context): RecorderSettings {
            val prefs = context.defaultPrefs()
            return RecorderSettings(
                resolution = snapToOption(prefs.getStringSafe(KEY_RESOLUTION), RESOLUTION_OPTIONS, 1080),
                // 枚举字段一律经 fromPref 归一化后写入：这样构造函数里的 require 断言
                // 在任何输入下都成立，非法存量值在此处就被纠正为默认，不会流到下游的穷尽 when。
                resolutionMode = ResolutionMode.fromPref(
                    prefs.getStringSafe(KEY_RESOLUTION_MODE)
                ).prefValue,
                frameRate = snapToOption(prefs.getStringSafe(KEY_FRAME_RATE), FRAME_RATE_OPTIONS, 30),
                bitRate = snapToOption(prefs.getStringSafe(KEY_BITRATE), BIT_RATE_OPTIONS, 8),
                audioSource = resolveAudioSource(prefs),
                replayDuration = snapToOption(prefs.getStringSafe(KEY_REPLAY_DURATION), REPLAY_DURATION_OPTIONS, 30),
                orientation = CaptureOrientation.fromPref(
                    prefs.getStringSafe(KEY_ORIENTATION)
                ).prefValue,

                codec = VideoCodec.fromPref(prefs.getStringSafe(KEY_CODEC)).prefValue,
                encoderPreference = EncoderPreference.fromPref(
                    prefs.getStringSafe(KEY_ENCODER_PREFERENCE)
                ).prefValue,
                toastNotify = if (prefs.getStringSafe(KEY_TOAST_NOTIFY) == VALUE_OFF) VALUE_OFF else VALUE_ON,
                contentRotation = if (prefs.getStringSafe(KEY_CONTENT_ROTATION) == VALUE_OFF) VALUE_OFF else VALUE_ON,
                audioMonitor = if (prefs.getStringSafe(KEY_AUDIO_MONITOR) == VALUE_ON) VALUE_ON else VALUE_OFF,
                shakeToSave = if (prefs.getStringSafe(KEY_SHAKE_TO_SAVE) == VALUE_ON) VALUE_ON else VALUE_OFF,
                shakeStrength = parseShakeStrength(prefs.getStringSafe(KEY_SHAKE_STRENGTH))
            )
        }
    }
}
