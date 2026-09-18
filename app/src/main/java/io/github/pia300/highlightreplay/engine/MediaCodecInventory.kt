package io.github.pia300.highlightreplay.engine

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import io.github.pia300.highlightreplay.data.EncoderPreference

/**
 * 软件编码器的名称前缀：API 26-28 无法通过属性区分软硬，用命名约定兜底。
 * `OMX.google.*` 与 `c2.android.*` 是 Android 内置的软件编解码器命名空间。
 */
private val SOFTWARE_CODEC_PREFIXES = listOf("OMX.google.", "c2.android.")

/**
 * 判断编码器是否为硬件实现。
 *
 * - API 29+：用 `isHardwareAccelerated`（比 `isSoftwareOnly` 更准）。
 * - API 26-28：两个属性都不可用（均为 API 29 引入），改用名称前缀判据；
 *   未命中软件前缀的编码器按硬件处理（厂商硬编命名各异，无法穷举）。
 */
internal fun isHardwareEncoder(info: MediaCodecInfo): Boolean = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        info.isHardwareAccelerated
    } else {
        SOFTWARE_CODEC_PREFIXES.none { info.name.startsWith(it, ignoreCase = true) }
    }
} catch (_: Exception) {
    // 个别 vendor 实现会抛异常：按非硬件处理，让调用方回退。
    false
}

/** 把 MediaCodecInfo 转成纯数据候选；能力查询失败时各范围字段为 null。 */
private fun toCandidate(info: MediaCodecInfo, mime: String): CodecCandidate {
    val caps = try {
        if (info.supportedTypes.any { it.equals(mime, ignoreCase = true) }) {
            info.getCapabilitiesForType(mime).videoCapabilities
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }
    return CodecCandidate(
        name = info.name,
        hardware = isHardwareEncoder(info),
        minWidth = caps?.supportedWidths?.lower,
        maxWidth = caps?.supportedWidths?.upper,
        minHeight = caps?.supportedHeights?.lower,
        maxHeight = caps?.supportedHeights?.upper,
        widthAlignment = caps?.widthAlignment,
        heightAlignment = caps?.heightAlignment
    )
}

/**
 * 按 [preference] 规划编码器与输出尺寸。
 *
 * - [EncoderPreference.HARDWARE]：只考虑硬件编码器，跳过尺寸不满足的候选；无硬件可用时回退系统代选。
 * - [EncoderPreference.SOFTWARE]：只考虑软件编码器，同样跳过尺寸不满足的候选；无软件可用时回退系统代选。
 * - [EncoderPreference.AUTO]：由系统选定编码器（`findEncoderForFormat`），再按其能力钳制尺寸。
 *   系统未给出编码器时不钳制尺寸，交由 `MediaCodec.createEncoderByType` 决定。
 *
 * @param mime 视频编码 MIME（如 `video/avc`、`video/hevc`）。
 * @param wantWidth 请求的输出宽，可能被编码器能力钳制。
 * @param wantHeight 请求的输出高，可能被编码器能力钳制。
 * @param preference 编码器类型偏好。
 * @param preferHardwareOnly 为 true 时只接受硬件编码器（供 `isCodecSupported` 复用）。
 */
internal fun planEncoder(
    mime: String,
    wantWidth: Int,
    wantHeight: Int,
    preference: EncoderPreference,
    preferHardwareOnly: Boolean = false
): EncoderPlan {
    val codecList = try {
        MediaCodecList(MediaCodecList.REGULAR_CODECS)
    } catch (_: Exception) {
        null
    }
    val infos = try {
        codecList?.codecInfos
            ?.filter { it.isEncoder && it.supportedTypes.any { t -> t.equals(mime, ignoreCase = true) } }
            .orEmpty()
    } catch (_: Exception) {
        emptyList()
    }
    val candidates = infos.map { toCandidate(it, mime) }

    // AUTO 且不强制硬件：由系统选定编码器，再按其能力钳制尺寸。
    if (preference == EncoderPreference.AUTO && !preferHardwareOnly) {
        val selectedName = try {
            codecList?.findEncoderForFormat(MediaFormat.createVideoFormat(mime, wantWidth, wantHeight))
        } catch (_: Exception) {
            null
        }
        val selected = candidates.firstOrNull { it.name == selectedName }
        // 系统代选成功时按其能力钳制；未给出编码器则保持请求尺寸，由 createEncoderByType 决定。
        val clamped = selected?.let { clampToCandidate(it, wantWidth, wantHeight) }
        return if (clamped != null) {
            EncoderPlan(
                selectedName, clamped.first, clamped.second, hardware = null,
                clamped = clamped.first != wantWidth || clamped.second != wantHeight
            )
        } else {
            EncoderPlan(selectedName, wantWidth, wantHeight, hardware = null, clamped = false)
        }
    }

    val wantHardware = preferHardwareOnly || preference == EncoderPreference.HARDWARE
    // 指定类型无可用编码器：回退系统代选，避免启动失败。
    return selectEncoder(candidates, wantWidth, wantHeight, wantHardware)
        ?: EncoderPlan(null, wantWidth, wantHeight, hardware = null, clamped = false)
}

/**
 * 按指定 codec 的能力钳制请求尺寸，用于 `MediaCodec` 实例已创建、需与其真实能力对齐配置尺寸的场景。
 * 能力未知时返回原尺寸。
 */
internal fun clampToCodec(
    info: MediaCodecInfo,
    mime: String,
    wantWidth: Int,
    wantHeight: Int
): Pair<Int, Int> =
    clampToCandidate(toCandidate(info, mime), wantWidth, wantHeight) ?: (wantWidth to wantHeight)
