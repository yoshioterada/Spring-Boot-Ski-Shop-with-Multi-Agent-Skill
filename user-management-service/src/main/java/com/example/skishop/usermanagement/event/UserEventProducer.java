package com.example.skishop.usermanagement.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UserEventProducer {

    static final String USER_CREATED_TOPIC = "user-created";
    static final String USER_UPDATED_TOPIC = "user-updated";
    static final String USER_DELETED_TOPIC = "user-deleted";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public UserEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishUserCreated(UserCreatedEvent event) {
        log.info("ユーザー作成イベントを発行します: userId={}", event.userId());
        kafkaTemplate.send(USER_CREATED_TOPIC, event.userId().toString(), event)
            .exceptionally(ex -> {
                log.error("ユーザー作成イベントの発行に失敗しました: userId={}", event.userId(), ex);
                return null;
            });
    }

    public void publishUserUpdated(UserUpdatedEvent event) {
        log.info("ユーザー更新イベントを発行します: userId={}", event.userId());
        kafkaTemplate.send(USER_UPDATED_TOPIC, event.userId().toString(), event)
            .exceptionally(ex -> {
                log.error("ユーザー更新イベントの発行に失敗しました: userId={}", event.userId(), ex);
                return null;
            });
    }

    public void publishUserDeleted(UserDeletedEvent event) {
        log.info("ユーザー削除イベントを発行します: userId={}", event.userId());
        kafkaTemplate.send(USER_DELETED_TOPIC, event.userId().toString(), event)
            .exceptionally(ex -> {
                log.error("ユーザー削除イベントの発行に失敗しました: userId={}", event.userId(), ex);
                return null;
            });
    }
}
