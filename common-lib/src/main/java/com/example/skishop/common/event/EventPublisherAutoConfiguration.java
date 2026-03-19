package com.example.skishop.common.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class EventPublisherAutoConfiguration {

    @Bean
    @ConditionalOnClass(StreamBridge.class)
    @ConditionalOnProperty(name = "spring.cloud.stream.kafka.binder.brokers")
    public EventPublisher springCloudStreamEventPublisher(
            StreamBridge streamBridge,
            ObjectMapper objectMapper,
            @Value("${skishop.event.binding-name:domainEvents-out-0}") String defaultBindingName) {
        return new SpringCloudStreamEventPublisher(streamBridge, objectMapper, defaultBindingName);
    }

    @Bean
    @ConditionalOnClass(JdbcTemplate.class)
    @ConditionalOnProperty(name = "skishop.event.outbox.enabled", havingValue = "true")
    public EventPublisher outboxEventPublisher(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {
        return new OutboxEventPublisher(jdbcTemplate, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(EventPublisher.class)
    public EventPublisher loggingEventPublisher() {
        return new LoggingEventPublisher();
    }
}
