package com.zhiyu;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 「织愈」后端启动类。
 *
 * <p>M0/T0.1a 只做工程骨架，尚未引入 Spring AI、Redis、Spring Security。
 */
@SpringBootApplication
@MapperScan("com.zhiyu.modules.**.mapper")
public class ZhiyuApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZhiyuApplication.class, args);
    }
}
