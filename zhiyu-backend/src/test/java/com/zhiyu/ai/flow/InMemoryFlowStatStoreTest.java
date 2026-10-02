package com.zhiyu.ai.flow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link InMemoryFlowStatStore} 单元测试（T1.7 子步骤 1）。
 */
class InMemoryFlowStatStoreTest {

    @Test
    @DisplayName("首次 get 返回 null（由调用方初始化）")
    void testGet_NotExists_ReturnsNull() {
        InMemoryFlowStatStore store = new InMemoryFlowStatStore();
        assertNull(store.get(99999L));
    }

    @Test
    @DisplayName("save + get 回读一致")
    void testSaveAndGet() {
        InMemoryFlowStatStore store = new InMemoryFlowStatStore();

        StepStat stat = new StepStat();
        stat.setStepNo(2);
        stat.setDwellSeconds(150);
        stat.setRepeatCount(1);
        stat.setLastFlowSeconds(100);

        store.save(100L, stat);

        StepStat result = store.get(100L);
        assertNotNull(result);
        assertEquals(2, result.getStepNo());
        assertEquals(150, result.getDwellSeconds());
        assertEquals(1, result.getRepeatCount());
        assertEquals(100, result.getLastFlowSeconds());
    }

    @Test
    @DisplayName("clear 后再 get 返回 null")
    void testClear() {
        InMemoryFlowStatStore store = new InMemoryFlowStatStore();

        StepStat stat = new StepStat();
        stat.setStepNo(1);
        store.save(200L, stat);
        assertNotNull(store.get(200L));

        store.clear(200L);
        assertNull(store.get(200L));
    }

    @Test
    @DisplayName("并发 save 不丢数据（10 线程各写 100 次）")
    void testConcurrentSave() throws InterruptedException {
        InMemoryFlowStatStore store = new InMemoryFlowStatStore();
        int threadCount = 10;
        int writesPerThread = 100;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            pool.submit(() -> {
                try {
                    for (int j = 0; j < writesPerThread; j++) {
                        StepStat stat = new StepStat();
                        stat.setStepNo(threadId);
                        stat.setDwellSeconds(j);
                        store.save((long) threadId, stat);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        pool.shutdown();

        // 每个线程 ID 的 key 都应该存在
        for (int i = 0; i < threadCount; i++) {
            StepStat result = store.get((long) i);
            assertNotNull(result, "key=" + i + " 不应为 null");
            assertEquals(i, result.getStepNo());
            assertEquals(writesPerThread - 1, result.getDwellSeconds());
        }
    }
}
