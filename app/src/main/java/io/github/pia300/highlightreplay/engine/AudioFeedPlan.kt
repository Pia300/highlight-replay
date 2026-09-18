package io.github.pia300.highlightreplay.engine

/**
 * 一块 PCM 能否整块写入编码器输入槽。
 *
 * 从 [AudioEncoder.feedEncoder] 抽出成纯函数，使「槽放不下整块」这条分支可被单元测试覆盖；
 * 原先它没有分支——直接 `buf.put(data, 0, size)`，容量不足会抛 `BufferOverflowException`
 * 被外层 `catch (e: Exception)` 吞掉，结果是该块不入队、不计入丢块、时间轴也不前进。
 *
 * 该函数的调用方必须保证：无论本函数返回 true 还是 false，时间轴都按 [blockSize] 前进
 * （见 `AudioEncoder` 中 feedEncoder 的 KDoc：丢块不能让音轨相对视频越来越早）。
 *
 * @param slotBytes 输入槽可写字节数（`ByteBuffer.remaining()`，已 `clear()`）。
 * @param blockSize 本次待写入的字节数。
 * @return true 表示整块可写入；false 表示装不下，应按丢块处置。
 */
internal fun audioBlockFitsInputSlot(slotBytes: Int, blockSize: Int): Boolean =
    blockSize <= slotBytes
