package com.fridgemind;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * FridgeMind 冰箱食材识别与管理系统 - 启动入口
 */
@SpringBootApplication
@MapperScan("com.fridgemind.mapper")
public class FridgeMindApplication {
    public static void main(String[] args) {
        SpringApplication.run(FridgeMindApplication.class, args);
    }
}
