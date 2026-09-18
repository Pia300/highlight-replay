package io.github.pia300.highlightreplay.engine

import io.github.pia300.highlightreplay.data.MediaData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 回放片段选取的单元测试。
 *
 * 分片缓冲保证快照起点落在关键帧边界上，故选取逻辑不再含回退搜索：
 * 本测试覆盖窗口裁剪、安全网与音频对齐三部分。
 */
class ReplayClipSelectorTest {

    /** 关键帧标志（MediaCodec.BUFFER_FLAG_KEY_FRAME）。 */
    private val keyFrame = 1

    /** 非关键帧标志。 */
    private val deltaFrame = 0

    /** 构造指定时间戳与关键帧属性的采样。 */
    private fun frame(timeStamp: Long, flags: Int = deltaFrame) =
        MediaData(ByteArray(4), timeStamp * 1000, flags, timeStamp)

    /** 时间戳序列，便于断言。 */
    private fun timestamps(frames: List<MediaData>) = frames.map { it.timeStamp }

    /** 窗口内帧被完整保留，起点与终点取自实际帧。 */
    @Test
    fun keepsFramesInsideWindow() {
        val video = listOf(
            frame(100, keyFrame),
            frame(200, deltaFrame),
            frame(300, deltaFrame),
            frame(400, deltaFrame)
        )

        val selection = selectFramesInWindow(video, emptyList(), 100, 300)!!

        assertEquals(listOf(100L, 200L, 300L), timestamps(selection.videoFrames))
        assertEquals(100L, selection.startTimestamp)
        assertEquals(300L, selection.endTimestamp)
    }

    /** 窗口起点晚于快照起点时，起点之前的帧被裁掉，输出从窗口内首个关键帧起头。 */
    @Test
    fun clipsFramesBeforeWindowStart() {
        val video = listOf(
            frame(100, keyFrame),
            frame(200, deltaFrame),
            frame(300, keyFrame),
            frame(400, deltaFrame)
        )

        val selection = selectFramesInWindow(video, emptyList(), 200, 400)!!

        assertEquals(listOf(300L, 400L), timestamps(selection.videoFrames))
        assertEquals(300L, selection.startTimestamp)
    }

    /** 窗口末端之后的帧被排除。 */
    @Test
    fun excludesFramesAfterWindowEnd() {
        val video = listOf(frame(100, keyFrame), frame(200, deltaFrame), frame(3000, deltaFrame))

        val selection = selectFramesInWindow(video, emptyList(), 100, 600)!!

        assertEquals(listOf(100L, 200L), timestamps(selection.videoFrames))
        assertEquals(200L, selection.endTimestamp)
    }

    /** 窗口边界为闭区间。 */
    @Test
    fun windowBoundsAreInclusive() {
        val video = listOf(frame(100, keyFrame), frame(200, deltaFrame))

        val selection = selectFramesInWindow(video, emptyList(), 100, 200)!!

        assertEquals(listOf(100L, 200L), timestamps(selection.videoFrames))
    }

    /**
     * 安全网：硬上限强制切出的分片不以关键帧开头且位于序列首位时，
     * 丢弃其前的包、从首个关键帧起拼接，保证输出可独立解码。
     */
    @Test
    fun skipsLeadingFramesBeforeFirstKeyFrame() {
        val video = listOf(
            frame(50, deltaFrame),
            frame(100, deltaFrame),
            frame(150, keyFrame),
            frame(200, deltaFrame)
        )

        val selection = selectFramesInWindow(video, emptyList(), 0, 300)!!

        assertEquals(listOf(150L, 200L), timestamps(selection.videoFrames))
        assertEquals(150L, selection.startTimestamp)
    }

    /** 序列中完全没有关键帧时无法选取。 */
    @Test
    fun returnsNullWithoutAnyKeyFrame() {
        val video = listOf(frame(100, deltaFrame), frame(200, deltaFrame))
        assertNull(selectFramesInWindow(video, emptyList(), 100, 200))
    }

    /** 缓冲为空时无法选取。 */
    @Test
    fun returnsNullWhenBufferIsEmpty() {
        assertNull(selectFramesInWindow(emptyList(), emptyList(), 0, 1000))
    }

    /**
     * 关键帧全部早于窗口起点、窗口内无关键帧时无法选取。
     *
     * 输出必须以窗口内的关键帧起头（封装层以首个样本为零点），故此时宁可返回 null。
     */
    @Test
    fun returnsNullWhenNoKeyFrameAtOrAfterWindowStart() {
        val video = listOf(frame(100, keyFrame), frame(200, deltaFrame))

        assertNull(selectFramesInWindow(video, emptyList(), 150, 400))
    }

    /**
     * 输出首帧必为窗口内的首个关键帧，其之前的帧被丢弃。
     *
     * 这是封装层的硬要求：[Mp4Muxer] 以首个样本为时间轴零点，保留关键帧之前的帧会得到
     * 负时间戳并被钳制到 0 附近，导致整条时间轴后移（真机曾观察到首帧 PTS 为 0.788s）。
     */
    @Test
    fun startsAtFirstKeyFrameInsideWindow() {
        val video = listOf(
            frame(100, keyFrame),
            frame(200, deltaFrame),
            frame(300, keyFrame),
            frame(400, deltaFrame)
        )

        val selection = selectFramesInWindow(video, emptyList(), 150, 400)!!

        assertEquals(listOf(300L, 400L), timestamps(selection.videoFrames))
        assertEquals(300L, selection.startTimestamp)
    }

    /** 快照起点即窗口内首个关键帧时，不丢弃任何帧。 */
    @Test
    fun keepsAllFramesWhenSnapshotStartsAtKeyFrameInsideWindow() {
        val video = listOf(
            frame(200, keyFrame),
            frame(300, deltaFrame)
        )

        val selection = selectFramesInWindow(video, emptyList(), 100, 400)!!

        assertEquals(listOf(200L, 300L), timestamps(selection.videoFrames))
        assertEquals(200L, selection.startTimestamp)
    }

    /** 快照本身不含任何关键帧时无法选取（安全网兜底）。 */
    @Test
    fun returnsNullWhenSnapshotHasNoKeyFrame() {
        val video = listOf(frame(200, deltaFrame), frame(300, deltaFrame))
        assertNull(selectFramesInWindow(video, emptyList(), 150, 400))
    }

    /** 音频只保留与视频时间范围对齐的部分。 */
    @Test
    fun collectsAudioAlignedWithVideoRange() {
        val video = listOf(frame(100, keyFrame), frame(300, deltaFrame))
        val audio = listOf(
            frame(50),
            frame(100),
            frame(200),
            frame(300),
            frame(400)
        )

        val selection = selectFramesInWindow(video, audio, 100, 300)!!

        assertEquals(listOf(100L, 200L, 300L), timestamps(selection.audioFrames))
    }

    /** 无音频数据时返回空音频列表。 */
    @Test
    fun emptyAudioProducesEmptyList() {
        val video = listOf(frame(100, keyFrame))
        val selection = selectFramesInWindow(video, emptyList(), 0, 200)!!
        assertEquals(0, selection.audioFrames.size)
    }
}
