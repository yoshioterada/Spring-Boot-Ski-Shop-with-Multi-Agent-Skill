package com.example.skishop.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(basePackages = {"com.example.skishop.ai", "com.example.skishop.common"})
@EnableScheduling
public class AiSupportServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiSupportServiceApplication.class, args);
    }
}
