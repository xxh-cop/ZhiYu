package com.zhiyu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 「织愈」后端启动类。
 *
 * <p>M0/T0.1a 只做工程骨架，尚未引入 Spring AI、Redis、Spring Security。
 *
 * <p>Mapper 接口扫描由 {@code com.zhiyu.config.MybatisPlusConfig} 上的
 * {@code @MapperScan("com.zhiyu.modules.**.mapper")} 负责，<b>本类不要再加</b>：
 * 两处重复标注会导致 MyBatis 扫两遍，第二遍的候选被跳过并误报
 * 「No MyBatis mapper was found」。
 */
@SpringBootApplication
public class ZhiyuApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZhiyuApplication.class, args);
    }
}
