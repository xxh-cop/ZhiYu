package com.zhiyu.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * Jackson 全局序列化配置。
 *
 * <p>核心问题：雪花算法生成的 Long ID 超过 JavaScript 安全整数范围
 * （Number.MAX_SAFE_INTEGER = 2^53 - 1 = 9007199254740991），
 * 前端 JSON.parse 会丢失末尾精度（如 2105963972535631874 → 2105963972535632000）。
 *
 * <p>解法：全局把 Long / long 序列化为 String，前端拿到的是字符串而非数字，
 * 避免 JS Number 精度丢失。前端传 URL 路径参数时本来就是字符串，不受影响。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longToStringCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule();
            module.addSerializer(Long.class, new LongToStringSerializer());
            module.addSerializer(Long.TYPE, new LongToStringSerializer());
            builder.modulesToInstall(module);
        };
    }

    /**
     * Long → String 序列化器。
     */
    public static class LongToStringSerializer extends JsonSerializer<Long> {
        @Override
        public void serialize(Long value, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            gen.writeString(value.toString());
        }
    }
}
