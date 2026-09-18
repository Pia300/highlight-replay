package io.github.pia300.highlightreplay.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** CycleRingBuffer 的单元测试：覆盖容量校验、环形覆盖、快照顺序与字节预算淘汰。 */
class CycleRingBufferTest {

    /** 验证以 0 或负数构造时抛出 IllegalArgumentException。 */
    @Test
    fun capacityMustBePositive() {
        assertThrows(IllegalArgumentException::class.java) { CycleRingBuffer<Int>(0) }
        assertThrows(IllegalArgumentException::class.java) { CycleRingBuffer<Int>(-1) }
    }

    /** 验证缓冲区未满时快照按插入顺序返回所有元素。 */
    @Test
    fun snapshotKeepsInsertionOrderBeforeFull() {
        val buffer = CycleRingBuffer<Int>(4)
        buffer.put(1)
        buffer.put(2)
        buffer.put(3)
        assertEquals(listOf(1, 2, 3), buffer.snapshot())
    }

    /** 验证写满后继续写入覆盖最旧数据，快照只保留最新元素。 */
    @Test
    fun oldestIsOverwrittenWhenFull() {
        val buffer = CycleRingBuffer<Int>(3)
        (1..5).forEach(buffer::put)
        assertEquals(listOf(3, 4, 5), buffer.snapshot())
    }

    /** 验证容量为 1 时只保留最新写入的元素。 */
    @Test
    fun capacityOneKeepsOnlyLatest() {
        val buffer = CycleRingBuffer<Int>(1)
        buffer.put(1)
        buffer.put(2)
        assertEquals(listOf(2), buffer.snapshot())
    }

    /** 验证 clear 移除全部元素后仍可继续正常写入。 */
    @Test
    fun clearRemovesAllElements() {
        val buffer = CycleRingBuffer<Int>(2)
        buffer.put(1)
        buffer.put(2)
        buffer.clear()
        assertTrue(buffer.snapshot().isEmpty())
        buffer.put(3)
        assertEquals(listOf(3), buffer.snapshot())
    }

    /** 验证容量语义：写满后覆盖最旧元素，快照长度不超过构造时指定的容量。 */
    @Test
    fun capacityBoundsSnapshotSize() {

        val buffer = CycleRingBuffer<Int>(7, maxBytes = 1000, sizeOf = { it })
        (1..5).forEach(buffer::put)
        assertEquals(5, buffer.snapshot().size)

        // 写满并溢出：长度封顶在容量上，且保留的是最新的 7 个。
        (6..10).forEach(buffer::put)
        assertEquals(7, buffer.snapshot().size)
        assertEquals(listOf(4, 5, 6, 7, 8, 9, 10), buffer.snapshot())

        buffer.clear()
        assertTrue(buffer.snapshot().isEmpty())
    }

    /** 验证超出字节预算时按最旧优先淘汰，直至总大小不超过预算。 */
    @Test
    fun byteBudgetEvictsOldest() {
        val buffer = CycleRingBuffer<Int>(10, maxBytes = 100, sizeOf = { it })
        (1..10).forEach(buffer::put)
        assertEquals(10, buffer.snapshot().size)
        buffer.put(50)

        assertEquals(listOf(4, 5, 6, 7, 8, 9, 10, 50), buffer.snapshot())
        assertTrue(buffer.snapshot().sum() <= 100)
    }

    /** 验证字节预算与环形覆盖同时生效时的淘汰结果。 */
    @Test
    fun byteBudgetWorksWithOverwrite() {

        val buffer = CycleRingBuffer<Int>(2, maxBytes = 10, sizeOf = { it })
        buffer.put(5)
        buffer.put(5)
        buffer.put(3)
        assertEquals(listOf(5, 3), buffer.snapshot())
        buffer.put(10)
        assertEquals(listOf(10), buffer.snapshot())
    }

    /** 验证单元素超过预算时仍保留该最新元素，随后被较小写入挤掉。 */
    @Test
    fun byteBudgetKeepsNewestWhenSingleElementExceeds() {

        val buffer = CycleRingBuffer<Int>(4, maxBytes = 10, sizeOf = { it })
        buffer.put(100)
        assertEquals(listOf(100), buffer.snapshot())
        buffer.put(1)
        assertEquals(listOf(1), buffer.snapshot())
    }

    /** 验证缓冲区未满时字节预算也会淘汰最早元素。 */
    @Test
    fun byteBudgetEvictsBeforeFull() {

        val buffer = CycleRingBuffer<Int>(10, maxBytes = 10, sizeOf = { it })
        buffer.put(6)
        buffer.put(5)
        assertEquals(listOf(5), buffer.snapshot())
        buffer.put(4)
        assertEquals(listOf(5, 4), buffer.snapshot())

        buffer.put(3)
        buffer.put(2)
        assertEquals(listOf(4, 3, 2), buffer.snapshot())
    }

    /** 验证 clear 后字节预算统计被重置，后续写入重新按预算淘汰。 */
    @Test
    fun byteBudgetAfterClear() {

        val buffer = CycleRingBuffer<Int>(4, maxBytes = 10, sizeOf = { it })
        buffer.put(5)
        buffer.put(5)
        buffer.put(5)
        buffer.put(5)
        buffer.clear()
        buffer.put(6)
        buffer.put(6)
        assertEquals(listOf(6), buffer.snapshot())
        buffer.put(4)
        assertEquals(listOf(6, 4), buffer.snapshot())
    }
}
