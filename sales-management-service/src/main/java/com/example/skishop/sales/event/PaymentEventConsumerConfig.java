package com.example.skishop.sales.event;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.util.function.Consumer;

@Configuration
public class PaymentEventConsumerConfig {

    @Bean
    public Consumer<Message<String>> paymentEvents(PaymentEventHandler paymentEventHandler) {
        return paymentEventHandler::handle;
    }
}
