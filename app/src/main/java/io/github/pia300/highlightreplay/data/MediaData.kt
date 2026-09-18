package io.github.pia300.highlightreplay.data

import android.media.MediaCodec
import java.nio.ByteBuffer

/** 已编码的媒体采样（视频或音频）及其解码/封装元信息。 */
class MediaData(
    val data: ByteArray,
    // MediaCodec 输出时间戳：视频取自 eglPresentationTimeANDROID（elapsedRealtimeNanos 绝对时刻），
    // 音频以会话内样本数从 0 起算；封装时两轨各自按首样本归零（见 ReplaySaver.muxAll）。
    val presentationTimeUs: Long,
    // MediaCodec 输出标志（如关键帧标记）。
    val flags: Int,
    // 采样内容自身的单调时钟时刻（毫秒，SystemClock.elapsedRealtime）：视频取渲染呈现时刻，
    // 音频取时间轴零点加样本数换算值。回放窗口切分与音画对齐的基准；单调时钟不受系统对时/时区调整影响。
    val timeStamp: Long
) {
    /** 返回底层数据的 ByteBuffer 视图（新包装，位置恒为 0）。 */
    fun getByteBuffer(): ByteBuffer = ByteBuffer.wrap(data)

    /** 是否关键帧（同步帧）。 */
    fun isKeyFrame(): Boolean = (flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0

    override fun toString(): String =
        "MediaData(timeStamp=$timeStamp, size=${data.size}, key=${isKeyFrame()})"
}
