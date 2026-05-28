package com.example.skishop.payment;

import com.example.skishop.payment.config.PaymentGatewayProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = {
    "com.example.skishop.payment",
    "com.example.skishop.common"
})
@EnableConfigurationProperties(PaymentGatewayProperties.class)
public class PaymentCartApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentCartApplication.class, args);
    }
}
