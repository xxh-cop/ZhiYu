package com.zhiyu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/**
 * 「织愈」后端启动类。
 *
 * <p>T0.3 起已引入 Spring Security，但**不使用表单登录 / 数据库用户体系**：
 * 鉴权由 {@code com.zhiyu.security.JwtAuthFilter} + {@code SecurityConfig} 完成。
 *
 * <p>因此这里显式排除 {@link UserDetailsServiceAutoConfiguration}。
 * 该自动配置在没有 {@code UserDetailsService} Bean 时会生成一个随机密码并打印
 * 「Using generated security password: ...」——没有任何入口可用，只会让人误以为
 * 系统开了表单登录，属于噪声（T0.3 批次实测确认）。
 *
 * <p>Mapper 接口扫描由 {@code com.zhiyu.config.MybatisPlusConfig} 上的
 * {@code @MapperScan("com.zhiyu.modules.**.mapper")} 负责，<b>本类不要再加</b>：
 * 两处重复标注会导致 MyBatis 扫两遍，第二遍的候选被跳过并误报
 * 「No MyBatis mapper was found」。
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class ZhiyuApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZhiyuApplication.class, args);
    }
}
