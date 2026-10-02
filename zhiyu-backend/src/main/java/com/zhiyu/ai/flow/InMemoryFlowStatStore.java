package com.zhiyu.ai.flow;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存版心流统计存储（T1.7）。
 *
 * <p>始终作为 fallback 存在。单机内有效，重启即重置。
 * 用 {@link ConcurrentHashMap#compute} 保证读-改-写原子性。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "zhiyu.flow.store", havingValue = "memory", matchIfMissing = true)
public class InMemoryFlowStatStore implements FlowStatStore {

    private final ConcurrentHashMap<Long, StepStat> store = new ConcurrentHashMap<>();

    @Override
    public StepStat get(Long sessionId) {
        return store.get(sessionId);
    }

    @Override
    public void save(Long sessionId, StepStat stat) {
        store.put(sessionId, stat);
    }

    @Override
    public void clear(Long sessionId) {
        store.remove(sessionId);
    }

    @Override
    public String implementationName() {
        return "in-memory";
    }
}
