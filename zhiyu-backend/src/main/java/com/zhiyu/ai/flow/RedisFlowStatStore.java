package com.zhiyu.ai.flow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis 版心流统计存储（T1.7）。
 *
 * <p>生效条件：{@code zhiyu.flow.store=redis}。
 * 多实例共享心流状态，重启不丢失。
 *
 * <p>Key 设计：{@code flow:stat:{sessionId}}
 * Value：JSON 字符串（Jackson 序列化 {@link StepStat}）
 * TTL：2 小时（防止僵尸会话残留）
 *
 * <p>容错：Redis 异常直接抛出，由 {@link FallbackFlowStatStore} 装饰器捕获并降级到内存。
 *
 * <p>注意：本类不做读-改-写原子（Lua 脚本），因为心流检测逻辑较复杂，
 * 放在 Lua 脚本里维护成本高。原子性由 {@link FlowDetectionService} 的单线程调用保证
 *（同一会话的心跳是串行的，前端不会并发上报）。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "zhiyu.flow.store", havingValue = "redis")
public class RedisFlowStatStore implements FlowStatStore {

    private static final String KEY_PREFIX = "flow:stat:";
    private static final Duration TTL = Duration.ofHours(2);

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisFlowStatStore(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        log.info("RedisFlowStatStore 初始化（TTL={}）", TTL);
    }

    @Override
    public StepStat get(Long sessionId) {
        String json = redisTemplate.opsForValue().get(key(sessionId));
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, StepStat.class);
        } catch (JsonProcessingException e) {
            log.warn("RedisFlowStatStore 反序列化失败，返回 null: sessionId={}, err={}", sessionId, e.getMessage());
            return null;
        }
    }

    @Override
    public void save(Long sessionId, StepStat stat) {
        try {
            String json = objectMapper.writeValueAsString(stat);
            redisTemplate.opsForValue().set(key(sessionId), json, TTL);
        } catch (JsonProcessingException e) {
            log.warn("RedisFlowStatStore 序列化失败: sessionId={}, err={}", sessionId, e.getMessage());
            throw new RuntimeException("StepStat 序列化失败", e);
        }
    }

    @Override
    public void clear(Long sessionId) {
        redisTemplate.delete(key(sessionId));
    }

    @Override
    public String implementationName() {
        return "redis";
    }

    private String key(Long sessionId) {
        return KEY_PREFIX + sessionId;
    }
}
