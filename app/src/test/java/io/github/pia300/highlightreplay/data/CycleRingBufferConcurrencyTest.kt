package io.github.pia300.highlightreplay.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * CycleRingBuffer 的并发测试。
 *
 * 单写者按严格递增序号写入、多读者并发快照：断言每个快照都是“严格递增且互不重复”的子序列
 * 且不超过容量（索引回绕、覆盖写、字节预算淘汰任一出错都会破坏该不变量），
 * 并在写入结束后断言缓冲内容恰为最后 capacity 个元素。
 */
class CycleRingBufferConcurrencyTest {

    /** 并发快照下：无重复、严格递增、不超容量；写入结束后恰为最后 capacity 个元素。 */
    @Test(timeout = 30_000)
    fun snapshotStaysOrderedAndBoundedUnderConcurrentReads() {
        val capacity = 64
        val totalWrites = 20_000
        val buffer = CycleRingBuffer<Long>(capacity)
        val failure = AtomicReference<Throwable?>(null)
        val startGate = CountDownLatch(1)
        val writerDone = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(4)
        try {
            val writer = pool.submit {
                startGate.await()
                try {
                    repeat(totalWrites) { buffer.put(it.toLong()) }
                } catch (t: Throwable) {
                    failure.compareAndSet(null, t)
                } finally {
                    writerDone.countDown()
                }
            }
            val readers = (0 until 3).map {
                pool.submit {
                    startGate.await()
                    try {

                        // 先做至少一次快照，再持续读到写者结束，保证与写入真正重叠。
                        var iterations = 0
                        do {
                            val snap = buffer.snapshot()
                            check(snap.size <= capacity) { "snapshot exceeded capacity: ${snap.size}" }
                            for (i in 1 until snap.size) {
                                // 单写者单调递增：出现相等或逆序即索引/覆盖逻辑被并发破坏。
                                check(snap[i] > snap[i - 1]) {
                                    "snapshot not strictly increasing at $i: ${snap[i - 1]} -> ${snap[i]}"
                                }
                            }
                            iterations++
                        } while (writerDone.count > 0L)
                        check(iterations > 0) { "reader performed no snapshot" }
                    } catch (t: Throwable) {
                        failure.compareAndSet(null, t)
                    }
                }
            }
            startGate.countDown()
            writer.get(20, TimeUnit.SECONDS)
            readers.forEach { it.get(20, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
            assertTrue("thread pool did not terminate", pool.awaitTermination(5, TimeUnit.SECONDS))
        }
        failure.get()?.let { throw AssertionError("concurrent snapshot broke buffer invariants", it) }

        // 写满后内容应为最后 capacity 个元素，且顺序正确。
        val expected = (totalWrites - capacity until totalWrites).map { it.toLong() }
        assertEquals(expected, buffer.snapshot())
    }

    /** 并发快照下任意时刻的总字节数都不超过 maxBytes。 */
    @Test(timeout = 30_000)
    fun snapshotsNeverExceedByteBudget() {
        val maxBytes = 1_000L
        val elementBytes = 10
        val capacity = 128
        val buffer = CycleRingBuffer<Long>(capacity, maxBytes = maxBytes, sizeOf = { elementBytes })
        val failure = AtomicReference<Throwable?>(null)
        val startGate = CountDownLatch(1)
        val writerDone = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(3)
        try {
            val writer = pool.submit {
                startGate.await()
                try {
                    repeat(10_000) { buffer.put(it.toLong()) }
                } catch (t: Throwable) {
                    failure.compareAndSet(null, t)
                } finally {
                    writerDone.countDown()
                }
            }
            val readers = (0 until 2).map {
                pool.submit {
                    startGate.await()
                    try {
                        do {
                            val bytes = buffer.snapshot().size.toLong() * elementBytes
                            check(bytes <= maxBytes) { "byte budget exceeded under concurrency: $bytes" }
                        } while (writerDone.count > 0L)
                    } catch (t: Throwable) {
                        failure.compareAndSet(null, t)
                    }
                }
            }
            startGate.countDown()
            writer.get(20, TimeUnit.SECONDS)
            readers.forEach { it.get(20, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
            assertTrue("thread pool did not terminate", pool.awaitTermination(5, TimeUnit.SECONDS))
        }
        failure.get()?.let { throw AssertionError("concurrent access broke byte budget", it) }

        // 收尾态同样受预算约束。
        assertTrue(buffer.snapshot().size.toLong() * elementBytes <= maxBytes)
    }
}
