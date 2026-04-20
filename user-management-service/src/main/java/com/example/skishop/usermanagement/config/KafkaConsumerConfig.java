package com.example.skishop.usermanagement.config;

import com.example.skishop.usermanagement.consumer.UserEventConsumer;
import com.example.skishop.usermanagement.service.UserRegistrationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("kafka")
public class KafkaConsumerConfig {

    @Bean
    public UserEventConsumer userEventConsumer(ObjectMapper objectMapper,
                                               UserRegistrationService userRegistrationService) {
        return new UserEventConsumer(objectMapper, userRegistrationService);
    }
}
