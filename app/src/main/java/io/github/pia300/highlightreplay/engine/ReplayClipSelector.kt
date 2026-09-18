package io.github.pia300.highlightreplay.engine

import io.github.pia300.highlightreplay.data.MediaData

/** 回放窗口内选取到的视频帧及其对齐的音频帧。 */
internal data class ReplayFrameSelection(
    /** 从关键帧起头的视频帧，按原缓冲顺序排列。 */
    val videoFrames: List<MediaData>,
    /** 与视频时间范围对齐的音频帧。 */
    val audioFrames: List<MediaData>,
    /** 首帧时间戳（毫秒，单调时钟）。 */
    val startTimestamp: Long,
    /** 末帧时间戳（毫秒，单调时钟）。 */
    val endTimestamp: Long
)

/**
 * 从 [videoList] 与 [audioList] 中选取 [targetStartTime]..[endTime] 窗口内的可解码视频帧
 * 及对齐的音频帧。
 *
 * 分片缓冲保证了分片边界即关键帧边界，且快照起点落在分片边界上，因此本函数**无需回退搜索**：
 * 窗口内首个关键帧即输出首帧。
 *
 * 输出**必从窗口内首个关键帧起头**，这是封装层的硬要求：[Mp4Muxer] 以首个样本为时间轴零点，
 * 若保留关键帧之前的帧，其时间戳为负并被钳制到 0 附近，会把整条时间轴后移。
 *
 * 唯一的例外是编码器长期不补发关键帧时被硬上限强制切出的分片：它不以关键帧开头，
 * 且可能位于序列首位。此时从首个关键帧起拼接（丢弃其前的包），保证输出仍可独立解码。
 *
 * @param videoList 视频帧快照，按时间升序；起点正常情况下为关键帧。
 * @param audioList 音频缓冲快照，按时间升序。
 * @param targetStartTime 回放窗口起点（毫秒，单调时钟）。
 * @param endTime 回放窗口终点（毫秒，单调时钟）。
 * @return 窗口内无可解码帧时返回 null。
 */
internal fun selectFramesInWindow(
    videoList: List<MediaData>,
    audioList: List<MediaData>,
    targetStartTime: Long,
    endTime: Long
): ReplayFrameSelection? {
    // 从窗口内**首个关键帧**起头：封装层以首个样本为时间轴零点，
    // 若保留关键帧之前的帧，其时间戳为负并被钳制到 0 附近，会把整条时间轴后移。
    val firstKeyIndex = videoList.indexOfFirst {
        it.isKeyFrame() && it.timeStamp >= targetStartTime
    }
    if (firstKeyIndex < 0) return null

    val fromStartKeyFrame = videoList.subList(firstKeyIndex, videoList.size)
    val videoFrames = fromStartKeyFrame.takeWhile { it.timeStamp <= endTime }
    if (videoFrames.isEmpty()) return null

    val startTimestamp = videoFrames.first().timeStamp
    val endTimestamp = videoFrames.last().timeStamp

    // 收集与视频时间范围对齐的音频帧，保持音画同步。
    val audioFrames = audioList.filter { it.timeStamp in startTimestamp..endTimestamp }

    return ReplayFrameSelection(
        videoFrames = videoFrames,
        audioFrames = audioFrames,
        startTimestamp = startTimestamp,
        endTimestamp = endTimestamp
    )
}
