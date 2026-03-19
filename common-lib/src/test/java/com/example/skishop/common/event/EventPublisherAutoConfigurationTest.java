package com.example.skishop.common.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EventPublisherAutoConfiguration - 自動構成")
class EventPublisherAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(EventPublisherAutoConfiguration.class));

    @Test
    @DisplayName("Bean 未定義時に LoggingEventPublisher がフォールバックとして登録される")
    void should_registerLoggingPublisher_when_noBeanDefined() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(EventPublisher.class);
            assertThat(context.getBean(EventPublisher.class)).isInstanceOf(LoggingEventPublisher.class);
        });
    }

    @Test
    @DisplayName("カスタム Bean 定義時に LoggingEventPublisher は登録されない")
    void should_notRegisterLoggingPublisher_when_customBeanDefined() {
        contextRunner
                .withBean(EventPublisher.class, () -> new EventPublisher() {
                    @Override
                    public <T> void publish(DomainEvent<T> event) {
                        // custom implementation
                    }
                })
                .run(context -> {
                    assertThat(context).hasSingleBean(EventPublisher.class);
                    assertThat(context.getBean(EventPublisher.class))
                            .isNotInstanceOf(LoggingEventPublisher.class);
                });
    }

    @Test
    @DisplayName("Kafka ブローカー未設定時に SpringCloudStreamEventPublisher は登録されない")
    void should_notRegisterKafkaPublisher_when_brokersPropertyAbsent() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(EventPublisher.class);
            assertThat(context.getBean(EventPublisher.class))
                    .isNotInstanceOf(SpringCloudStreamEventPublisher.class);
        });
    }

    @Test
    @DisplayName("Outbox 未有効時に OutboxEventPublisher は登録されない")
    void should_notRegisterOutboxPublisher_when_outboxPropertyAbsent() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(EventPublisher.class);
            assertThat(context.getBean(EventPublisher.class))
                    .isNotInstanceOf(OutboxEventPublisher.class);
        });
    }
}
