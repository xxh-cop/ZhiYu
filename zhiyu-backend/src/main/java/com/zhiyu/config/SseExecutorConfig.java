package com.zhiyu.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * SSE 流式对话专用线程池配置（T1.3a）。
 *
 * <p>不阻塞 Tomcat 请求线程：SSE 流式对话提交到独立线程池执行。
 *
 * <p>容量选择（2026-11-03 压测修复，见 docs/perf-baseline-20261102.md「修复后」）：
 * 原 core=4 / max=16 / queue=100 时，Spring 线程池按「core 满 → 入队 → 队列满才扩」的顺序扩容，
 * 队列 100 永远排不满，实际并发恒为 4，max=16 是「假 max」。修复后 core=max=16：
 * 线程直接常驻 16 条，并发请求立即获得线程而不是先排队；queue=32 只作为
 * 超过 16 并发时的短突发缓冲；再超出走 CallerRunsPolicy 由 Tomcat 线程兜底，
 * 宁可让调用方变慢也不丢弃 SSE 请求。
 */
@Configuration
public class SseExecutorConfig {

    @Bean("sseExecutor")
    public ThreadPoolTaskExecutor sseExecutor(
            @Value("${zhiyu.sse.executor.core-size:16}") int coreSize,
            @Value("${zhiyu.sse.executor.max-size:16}") int maxSize,
            @Value("${zhiyu.sse.executor.queue-capacity:32}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("sse-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
