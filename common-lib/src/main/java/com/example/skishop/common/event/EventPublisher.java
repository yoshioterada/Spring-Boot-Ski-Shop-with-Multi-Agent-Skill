package com.example.skishop.common.event;

/**
 * ドメインイベントの発行を抽象化するインターフェース。
 * <p>
 * 実装は LoggingEventPublisher（スタブ）、SpringCloudStreamEventPublisher（Kafka）、
 * OutboxEventPublisher（Transactional Outbox）に差し替え可能。
 * </p>
 *
 * @see DomainEvent
 * @see LoggingEventPublisher
 */
public interface EventPublisher {

    /**
     * ドメインイベントを同期的に発行する。
     * トランザクション境界内で呼び出されることを想定。
     *
     * @param event 発行するドメインイベント（null 不可）
     * @throws EventPublishException イベント発行に失敗した場合
     */
    <T> void publish(DomainEvent<T> event);
}
