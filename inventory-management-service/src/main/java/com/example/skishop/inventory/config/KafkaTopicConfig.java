package com.example.skishop.inventory.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka トピック設定。
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic productCreatedTopic() {
        return TopicBuilder.name("inventory.product.created")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic productUpdatedTopic() {
        return TopicBuilder.name("inventory.product.updated")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic inventoryUpdatedTopic() {
        return TopicBuilder.name("inventory.stock.updated")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic lowStockAlertTopic() {
        return TopicBuilder.name("inventory.stock.low-alert")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
