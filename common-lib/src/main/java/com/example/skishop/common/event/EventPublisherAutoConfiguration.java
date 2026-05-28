package com.example.skishop.common.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@EnableScheduling
public class EventPublisherAutoConfiguration {

    @Bean
    @ConditionalOnClass(StreamBridge.class)
    @ConditionalOnProperty(name = "spring.cloud.stream.kafka.binder.brokers")
    @ConditionalOnProperty(name = "skishop.event.outbox.enabled", havingValue = "false", matchIfMissing = true)
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
    @ConditionalOnClass(StreamBridge.class)
    @ConditionalOnProperty(name = "skishop.event.outbox.relay.enabled", havingValue = "true")
    public OutboxEventRelay outboxEventRelay(
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate,
            StreamBridge streamBridge,
            ObjectMapper objectMapper,
            ObjectProvider<MeterRegistry> meterRegistry,
            @Value("${skishop.event.binding-name:domainEvents-out-0}") String defaultBindingName,
            @Value("${skishop.event.outbox.relay.batch-size:100}") int batchSize,
            @Value("${skishop.event.outbox.relay.max-retries:10}") int maxRetries,
            @Value("${skishop.event.outbox.relay.base-delay-ms:5000}") long baseDelayMs,
            @Value("${skishop.event.outbox.relay.max-delay-ms:300000}") long maxDelayMs) {
        OutboxRelayProperties properties = OutboxRelayProperties.ofMillis(
                batchSize, maxRetries, baseDelayMs, maxDelayMs);
        return new OutboxEventRelay(
                jdbcTemplate,
                transactionTemplate,
                streamBridge,
                objectMapper,
                meterRegistry.getIfAvailable(),
                defaultBindingName,
                properties);
    }

    @Bean
    @ConditionalOnMissingBean(EventPublisher.class)
    public EventPublisher loggingEventPublisher() {
        return new LoggingEventPublisher();
    }
}
