package io.github.pia300.highlightreplay.engine

/**
 * 音频时间轴：按已送入编码器的样本数换算微秒级 PTS。
 *
 * 时间轴在送入成功、输入缓冲占满、输入缓冲不可用时都按真实经过的样本数前进，
 * 使丢块不会让音轨相对视频逐渐提前。
 *
 * @param sampleRate 采样率（Hz）。
 * @param channelCount 声道数。
 */
internal class AudioPtsClock(
    private val sampleRate: Int,
    private val channelCount: Int
) {

    /** 已送入的采样总数（含全部声道）。 */
    var samplesFed: Long = 0L
        private set

    /** 当前时间轴位置（微秒）。 */
    fun currentPtsUs(): Long = samplesFed * 1_000_000L / (channelCount * sampleRate)

    /** 按块字节数推进时间轴（16 位样本，每样本 2 字节）。 */
    fun advance(bytes: Int) {
        samplesFed += bytes / 2
    }

    /** 会话开始时清零。 */
    fun reset() {
        samplesFed = 0L
    }
}
