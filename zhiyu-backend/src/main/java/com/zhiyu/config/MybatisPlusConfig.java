package com.zhiyu.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置。
 *
 * <p>包含两件事：Mapper 接口扫描与分页插件注册。
 *
 * <p>扫描路径 {@code com.zhiyu.modules.**.mapper} 对应 AGENTS.md §2 的包结构约定
 * —— 每个业务模块把自己的 Mapper 放在 {@code modules/<模块>/mapper/} 下。
 */
@Configuration
@MapperScan("com.zhiyu.modules.**.mapper")
public class MybatisPlusConfig {

    /**
     * MyBatis-Plus 拦截器链。
     *
     * <p>当前只挂分页插件。后续如需多租户、乐观锁、防全表更新等，
     * <b>注意顺序</b>：分页插件必须放在最后（官方要求）。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
