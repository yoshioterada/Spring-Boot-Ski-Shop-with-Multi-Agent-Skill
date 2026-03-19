package com.example.skishop.mailsend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = {
        "com.example.skishop.mailsend",
        "com.example.skishop.common"
})
@ConfigurationPropertiesScan(basePackages = "com.example.skishop.mailsend")
public class MailSendApplication {

    public static void main(String[] args) {
        SpringApplication.run(MailSendApplication.class, args);
    }
}
