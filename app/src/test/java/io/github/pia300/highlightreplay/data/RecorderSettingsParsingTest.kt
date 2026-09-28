package io.github.pia300.highlightreplay.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 持久化数值/枚举解析：脏值、越界值与旧值必须吸附到合法档位，且结果始终落在引擎允许范围内。
 */
class RecorderSettingsParsingTest {

    /** 缺失或非数字的存储值回退默认档位。 */
    @Test
    fun snapToOptionFallsBackOnMissingOrInvalid() {
        val options = RecorderSettings.RESOLUTION_OPTIONS
        assertEquals(1080, RecorderSettings.snapToOption(null, options, 1080))
        assertEquals(1080, RecorderSettings.snapToOption("abc", options, 1080))
        assertEquals(1080, RecorderSettings.snapToOption("", options, 1080))
        assertEquals(1080, RecorderSettings.snapToOption("  ", options, 1080))
    }

    /** 合法但非档位的数值吸附到最近档位（含极值与负数）。 */
    @Test
    fun snapToOptionSnapsToNearestSupportedStep() {
        val res = RecorderSettings.RESOLUTION_OPTIONS
        assertEquals(720, RecorderSettings.snapToOption("480", res, 1080))
        assertEquals(1080, RecorderSettings.snapToOption("1000", res, 1080))
        assertEquals(1440, RecorderSettings.snapToOption("999999", res, 1080))
        assertEquals(720, RecorderSettings.snapToOption("-5", res, 1080))
        assertEquals(1080, RecorderSettings.snapToOption("not-a-number", res, 1080))

        // 45 与 30/60 等距：minByOrNull 取先出现的档位（30）——平局语义固定在测试里。
        assertEquals(30, RecorderSettings.snapToOption("45", RecorderSettings.REPLAY_DURATION_OPTIONS, 30))
        assertEquals(60, RecorderSettings.snapToOption("50", RecorderSettings.REPLAY_DURATION_OPTIONS, 30))
        assertEquals(16, RecorderSettings.snapToOption("100", RecorderSettings.BIT_RATE_OPTIONS, 8))
        assertEquals(24, RecorderSettings.snapToOption("15", RecorderSettings.FRAME_RATE_OPTIONS, 30))
    }

    /** 枚举解析：未知/空值回退默认，合法值正确映射。 */
    @Test
    fun enumParsersFallBackToDefaultsOnUnknownValues() {
        assertEquals(VideoCodec.H264, VideoCodec.fromPref(null))
        assertEquals(VideoCodec.H264, VideoCodec.fromPref("vp9"))
        assertEquals(VideoCodec.H265, VideoCodec.fromPref("h265"))

        assertEquals(AudioSourceMode.NONE, AudioSourceMode.fromPref(null))
        assertEquals(AudioSourceMode.INTERNAL, AudioSourceMode.fromPref("internal"))

        assertEquals(CaptureOrientation.AUTO, CaptureOrientation.fromPref("bogus"))
        assertEquals(CaptureOrientation.PORTRAIT, CaptureOrientation.fromPref("portrait"))

        assertEquals(ResolutionMode.STANDARD, ResolutionMode.fromPref(null))
        assertEquals(ResolutionMode.ADAPTIVE, ResolutionMode.fromPref("adaptive"))
    }

    /** 吸附结果必须落在引擎物理范围内（构造函数的 require 依赖该不变量）。 */
    @Test
    fun snappedValuesStayInsideEngineRanges() {
        val fuzz = listOf(
            "-2147483648", "2147483647", "0", "1", "479", "720", "1081", "1439", "1441", "4321"
        )
        fuzz.forEach { raw ->
            val resolution = RecorderSettings.snapToOption(raw, RecorderSettings.RESOLUTION_OPTIONS, 1080)
            assertTrue("resolution $resolution out of range (raw=$raw)", resolution in RecorderSettings.RESOLUTION_RANGE)

            val frameRate = RecorderSettings.snapToOption(raw, RecorderSettings.FRAME_RATE_OPTIONS, 30)
            assertTrue("frameRate $frameRate out of range (raw=$raw)", frameRate in RecorderSettings.FRAME_RATE_RANGE)

            val bitRate = RecorderSettings.snapToOption(raw, RecorderSettings.BIT_RATE_OPTIONS, 8)
            assertTrue("bitRate $bitRate out of range (raw=$raw)", bitRate in RecorderSettings.BIT_RATE_RANGE)

            val duration = RecorderSettings.snapToOption(raw, RecorderSettings.REPLAY_DURATION_OPTIONS, 30)
            assertTrue(
                "replayDuration $duration out of range (raw=$raw)",
                duration in RecorderSettings.REPLAY_DURATION_RANGE
            )
        }
    }

    /**
     * 构造函数的 require 必须覆盖**全部**枚举字段。
     *
     * 只查非空不足以挡住非法值：`VideoSizeCalculator.compute` 与 `orient` 用穷尽 `when`
     * 消费 `ResolutionMode` / `CaptureOrientation`，非法字符串不会抛异常，而是落到 `else` 分支
     * **静默退回 AUTO / STANDARD**——比崩溃更难查。故非法值必须在构造处就被拒绝。
     */
    @Test
    fun constructorRejectsInvalidEnumValues() {
        fun expectRejected(field: String, build: () -> RecorderSettings) {
            try {
                build()
                throw AssertionError("非法 $field 未被拒绝：构造函数接受了它")
            } catch (_: IllegalArgumentException) {
                // 期望路径
            }
        }

        expectRejected("codec") { RecorderSettings(codec = "vp9") }
        expectRejected("orientation") { RecorderSettings(orientation = "portrait-typo") }
        expectRejected("resolutionMode") { RecorderSettings(resolutionMode = "adaptive-typo") }
        expectRejected("audioSource") { RecorderSettings(audioSource = "microphone") }
        expectRejected("encoderPreference") { RecorderSettings(encoderPreference = "gpu") }
        expectRejected("toastNotify") { RecorderSettings(toastNotify = "yes") }
        expectRejected("contentRotation") { RecorderSettings(contentRotation = "maybe") }
        expectRejected("shakeToSave") { RecorderSettings(shakeToSave = "yes") }
    }

    /** 触发力度是连续量：越界钳制到端点，缺失/非数字回退默认值。 */
    @Test
    fun shakeStrengthClampsToRangeAndFallsBack() {
        assertEquals(
            RecorderSettings.SHAKE_STRENGTH_DEFAULT,
            RecorderSettings.parseShakeStrength(null)
        )
        assertEquals(
            RecorderSettings.SHAKE_STRENGTH_DEFAULT,
            RecorderSettings.parseShakeStrength("abc")
        )
        assertEquals(
            RecorderSettings.SHAKE_STRENGTH_DEFAULT,
            RecorderSettings.parseShakeStrength("")
        )
        assertEquals(43, RecorderSettings.parseShakeStrength("43"))
        assertEquals(RecorderSettings.SHAKE_STRENGTH_RANGE.first, RecorderSettings.parseShakeStrength("-1"))
        assertEquals(RecorderSettings.SHAKE_STRENGTH_RANGE.last, RecorderSettings.parseShakeStrength("1000"))
    }

    /** 触发力度的硬边界同样在构造处拒绝越界值（解析路径已钳制，此处防新增构造路径绕过）。 */
    @Test
    fun constructorRejectsOutOfRangeShakeStrength() {
        listOf(-1, 101, Int.MAX_VALUE, Int.MIN_VALUE).forEach { value ->
            try {
                RecorderSettings(shakeStrength = value)
                throw AssertionError("越界力度未被拒绝：$value")
            } catch (_: IllegalArgumentException) {
                // 期望路径
            }
        }
    }

    /** 反向：每个枚举的合法 prefValue 都必须被接受，避免 require 写成过窄的白名单。 */
    @Test
    fun constructorAcceptsEveryLegalEnumPrefValue() {
        VideoCodec.entries.forEach { assertEquals(it, VideoCodec.fromPref(RecorderSettings(codec = it.prefValue).codec)) }
        CaptureOrientation.entries.forEach {
            assertEquals(it, CaptureOrientation.fromPref(RecorderSettings(orientation = it.prefValue).orientation))
        }
        ResolutionMode.entries.forEach {
            assertEquals(it, ResolutionMode.fromPref(RecorderSettings(resolutionMode = it.prefValue).resolutionMode))
        }
        AudioSourceMode.entries.forEach {
            assertEquals(it, AudioSourceMode.fromPref(RecorderSettings(audioSource = it.prefValue).audioSource))
        }
        EncoderPreference.entries.forEach {
            assertEquals(
                it,
                EncoderPreference.fromPref(RecorderSettings(encoderPreference = it.prefValue).encoderPreference)
            )
        }
    }
}
