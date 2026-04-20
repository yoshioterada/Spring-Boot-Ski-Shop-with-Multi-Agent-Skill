package com.example.skishop.sales;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {
    "com.example.skishop.sales",
    "com.example.skishop.common"
})
@EnableScheduling
public class SalesManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(SalesManagementApplication.class, args);
    }
}
