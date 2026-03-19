package com.example.skishop.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
    "com.example.skishop.payment",
    "com.example.skishop.common"
})
public class PaymentCartApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentCartApplication.class, args);
    }
}
