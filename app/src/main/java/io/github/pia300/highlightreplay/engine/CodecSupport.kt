package io.github.pia300.highlightreplay.engine

import io.github.pia300.highlightreplay.data.EncoderPreference
import java.util.concurrent.ConcurrentHashMap

// 探测结果进程内缓存：同一 MIME 的支持与否不随进程变化。
private val codecSupportCache = ConcurrentHashMap<String, Boolean>()

/** 该 MIME 是否有可用的硬件编码器；结果进程内缓存。 */
internal fun isCodecSupported(mimeType: String): Boolean {
    codecSupportCache[mimeType]?.let { return it }
    // 只认硬件编码器：软编在部分机型上占用极高 CPU，长时间录制会降频掉帧。
    // 判据与 MediaCodecInventory.isHardwareEncoder 一致；该函数在 API 26-28 上无法区分软硬，按硬件处理。
    val supported = planEncoder(
        mime = mimeType,
        wantWidth = 1920,
        wantHeight = 1080,
        preference = EncoderPreference.HARDWARE,
        preferHardwareOnly = true
    ).codecName != null
    codecSupportCache[mimeType] = supported
    return supported
}

/** 已缓存的探测结果；尚未探测过时返回 null，不触发编码器枚举。 */
internal fun cachedCodecSupport(mimeType: String): Boolean? = codecSupportCache[mimeType]
