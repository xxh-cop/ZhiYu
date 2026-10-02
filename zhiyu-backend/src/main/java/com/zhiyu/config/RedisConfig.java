package com.zhiyu.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置（T1.1a 最小引入）。
 *
 * <p>只定义 {@link RedisTemplate}（String 序列化），供 T1.1b 熔断器存取状态。
 * 不配置 Spring Cache、不配 @Cacheable。
 *
 * <p>Spring Boot 自动装配的 {@code StringRedisTemplate} 已够用，
 * 但显式声明 {@code RedisTemplate<String, String>} 更明确业务意图
 * （熔断计数器只存简单字符串 / JSON）。
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
}
