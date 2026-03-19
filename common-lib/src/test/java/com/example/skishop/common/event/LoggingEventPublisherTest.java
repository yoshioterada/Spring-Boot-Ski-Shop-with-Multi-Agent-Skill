package com.example.skishop.common.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("LoggingEventPublisher - ログ出力イベントパブリッシャー")
class LoggingEventPublisherTest {

    private final LoggingEventPublisher publisher = new LoggingEventPublisher();

    @Test
    @DisplayName("publish が例外を投げずにログ出力する")
    void should_publishWithoutException_when_validEvent() {
        // Arrange
        DomainEvent<String> event = DomainEvent.create("test.event", "test-service", "payload");

        // Act & Assert
        assertThatCode(() -> publisher.publish(event)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("複雑なペイロードでも正常に publish できる")
    void should_publishWithoutException_when_complexPayload() {
        // Arrange
        record TestPayload(String id, int count) {}
        DomainEvent<TestPayload> event = DomainEvent.create(
                "complex.event", "test-service", new TestPayload("abc", 42));

        // Act & Assert
        assertThatCode(() -> publisher.publish(event)).doesNotThrowAnyException();
    }
}
