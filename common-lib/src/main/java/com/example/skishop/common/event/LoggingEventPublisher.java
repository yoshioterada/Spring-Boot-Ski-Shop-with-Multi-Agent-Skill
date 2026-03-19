package com.example.skishop.common.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * EventPublisher のログ出力のみの実装。
 * Kafka 統合前の開発・テスト用スタブ。
 * <p>
 * 各サービスで具体的な EventPublisher Bean が定義されていない場合に
 * フォールバックとして使用される。
 * </p>
 */
public class LoggingEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingEventPublisher.class);

    @Override
    public <T> void publish(DomainEvent<T> event) {
        log.info("Event published: type={}, eventId={}, producer={}, correlationId={}",
                event.eventType(), event.eventId(), event.producer(), event.correlationId());
    }
}
