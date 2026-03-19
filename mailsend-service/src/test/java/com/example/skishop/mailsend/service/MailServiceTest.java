package com.example.skishop.mailsend.service;

import com.example.skishop.common.event.DomainEvent;
import com.example.skishop.common.event.EventPublisher;
import com.example.skishop.common.exception.BusinessRuleViolationException;
import com.example.skishop.common.exception.ResourceNotFoundException;
import com.example.skishop.mailsend.config.MailProperties;
import com.example.skishop.mailsend.model.MailLog;
import com.example.skishop.mailsend.model.MailStatus;
import com.example.skishop.mailsend.repository.MailLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MailServiceTest {

    @Mock private MailLogRepository mailLogRepository;
    @Mock private TemplateService templateService;
    @Mock private AzureEmailSender azureEmailSender;
    @Mock private EventPublisher eventPublisher;

    @Test
    @DisplayName("送信が失敗した場合、リトライして成功できる")
    void should_retryAndSucceed_when_retryableFailuresOccur() {
        // Arrange
        var mailProps = new MailProperties(new MailProperties.Retry(3, 10, 2.0), "http://localhost:3000");
        var meterRegistry = new SimpleMeterRegistry();
        var objectMapper = new ObjectMapper();

        var sleeper = (Sleeper) millis -> {
            // no-op for test
        };

        var service = new MailService(
                mailLogRepository,
                templateService,
                azureEmailSender,
                eventPublisher,
                objectMapper,
                mailProps,
                meterRegistry,
                sleeper);

        when(mailLogRepository.findByEventId("e1")).thenReturn(Optional.empty());
        when(mailLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(templateService.render(eq("password-reset"), any())).thenReturn(new RenderedMailContent("<p>ok</p>", "ok"));

        when(azureEmailSender.send(any(), any(), any(), any()))
                .thenThrow(new EmailSendException("429", new RuntimeException("throttle"), 429, 1L))
                .thenThrow(new EmailSendException("500", new RuntimeException("oops"), 500, null))
                .thenReturn("op-1");

        var inbound = new MailService.InboundMail(
                "e1",
                "PASSWORD_RESET_REQUESTED",
                "c1",
                "test@example.com",
                null,
                "password-reset",
                "パスワード再設定のご案内",
                Map.of("firstName", "太郎", "resetUrl", "http://localhost:3000/password/reset?token=t")
        );

        // Act
        service.sendInbound(inbound);

        // Assert
        verify(azureEmailSender, times(3)).send(any(), any(), any(), any());
        verify(eventPublisher, times(1)).publish(any());

        var logCaptor = ArgumentCaptor.forClass(com.example.skishop.mailsend.model.MailLog.class);
        verify(mailLogRepository, atLeastOnce()).save(logCaptor.capture());
        assertThat(logCaptor.getAllValues()).isNotEmpty();
        assertThat(meterRegistry.get("mail.sent.total").counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("既に処理済みのイベントIDの場合、重複としてスキップする")
    void should_skipDuplicate_when_eventIdAlreadyExists() {
        // Arrange
        var mailProps = new MailProperties(new MailProperties.Retry(3, 10, 2.0), "http://localhost:3000");
        var meterRegistry = new SimpleMeterRegistry();
        var objectMapper = new ObjectMapper();
        var sleeper = (Sleeper) millis -> {};
        var service = new MailService(
                mailLogRepository, templateService, azureEmailSender,
                eventPublisher, objectMapper, mailProps, meterRegistry, sleeper);

        var existingLog = new MailLog("ORDER_CONFIRMED", "dup-event-1", "c1",
                "test@example.com", null, "order-confirm", "ご注文確認",
                MailStatus.SENT, "{}");
        when(mailLogRepository.findByEventId("dup-event-1")).thenReturn(Optional.of(existingLog));

        var inbound = new MailService.InboundMail(
                "dup-event-1", "ORDER_CONFIRMED", "c1",
                "test@example.com", null, "order-confirm", "ご注文確認",
                Map.of("orderId", "123"));

        // Act
        service.sendInbound(inbound);

        // Assert
        verify(mailLogRepository, never()).save(any());
        verify(azureEmailSender, never()).send(any(), any(), any(), any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("無効なメールアドレスの場合、SKIPPEDステータスで保存する")
    void should_markSkipped_when_recipientEmailInvalid() {
        // Arrange
        var mailProps = new MailProperties(new MailProperties.Retry(3, 10, 2.0), "http://localhost:3000");
        var meterRegistry = new SimpleMeterRegistry();
        var objectMapper = new ObjectMapper();
        var sleeper = (Sleeper) millis -> {};
        var service = new MailService(
                mailLogRepository, templateService, azureEmailSender,
                eventPublisher, objectMapper, mailProps, meterRegistry, sleeper);

        when(mailLogRepository.findByEventId("e-invalid")).thenReturn(Optional.empty());
        when(mailLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var inbound = new MailService.InboundMail(
                "e-invalid", "PASSWORD_RESET_REQUESTED", "c2",
                "not-an-email", null, "password-reset", "パスワード再設定",
                Map.of("firstName", "太郎"));

        // Act
        service.sendInbound(inbound);

        // Assert
        var logCaptor = ArgumentCaptor.forClass(MailLog.class);
        verify(mailLogRepository, atLeastOnce()).save(logCaptor.capture());
        var savedLog = logCaptor.getAllValues().getLast();
        assertThat(savedLog.getStatus()).isEqualTo(MailStatus.SKIPPED);
        assertThat(savedLog.getErrorMessage()).contains("Invalid email address");
        verify(azureEmailSender, never()).send(any(), any(), any(), any());
    }

    @Test
    @DisplayName("リトライ不可能なエラーの場合、即座にFAILEDとなる")
    void should_failImmediately_when_nonRetryableErrorOccurs() {
        // Arrange
        var mailProps = new MailProperties(new MailProperties.Retry(3, 10, 2.0), "http://localhost:3000");
        var meterRegistry = new SimpleMeterRegistry();
        var objectMapper = new ObjectMapper();
        var sleeper = (Sleeper) millis -> {};
        var service = new MailService(
                mailLogRepository, templateService, azureEmailSender,
                eventPublisher, objectMapper, mailProps, meterRegistry, sleeper);

        when(mailLogRepository.findByEventId("e-400")).thenReturn(Optional.empty());
        when(mailLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(templateService.render(eq("password-reset"), any()))
                .thenReturn(new RenderedMailContent("<p>ok</p>", "ok"));
        when(azureEmailSender.send(any(), any(), any(), any()))
                .thenThrow(new EmailSendException("Bad Request", new RuntimeException("bad"), 400, null));

        var inbound = new MailService.InboundMail(
                "e-400", "PASSWORD_RESET_REQUESTED", "c3",
                "user@example.com", null, "password-reset", "パスワード再設定",
                Map.of("firstName", "太郎", "resetUrl", "http://localhost:3000/password/reset?token=t"));

        // Act
        service.sendInbound(inbound);

        // Assert
        verify(azureEmailSender, times(1)).send(any(), any(), any(), any());

        var logCaptor = ArgumentCaptor.forClass(MailLog.class);
        verify(mailLogRepository, atLeastOnce()).save(logCaptor.capture());
        var lastSaved = logCaptor.getAllValues().getLast();
        assertThat(lastSaved.getStatus()).isEqualTo(MailStatus.FAILED);
        assertThat(lastSaved.getErrorMessage()).contains("Bad Request");

        @SuppressWarnings("unchecked")
        var eventCaptor = ArgumentCaptor.forClass((Class<DomainEvent<?>>) (Class<?>) DomainEvent.class);
        verify(eventPublisher, times(1)).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType()).isEqualTo("MailSendFailed");
        assertThat(meterRegistry.get("mail.failed.total").counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("統計情報をリクエストした場合、正しい集計結果を返す")
    void should_returnStats_when_statsRequested() {
        // Arrange
        var mailProps = new MailProperties(new MailProperties.Retry(3, 10, 2.0), "http://localhost:3000");
        var meterRegistry = new SimpleMeterRegistry();
        var objectMapper = new ObjectMapper();
        var sleeper = (Sleeper) millis -> {};
        var service = new MailService(
                mailLogRepository, templateService, azureEmailSender,
                eventPublisher, objectMapper, mailProps, meterRegistry, sleeper);

        when(mailLogRepository.countSent()).thenReturn(80L);
        when(mailLogRepository.countFailed()).thenReturn(20L);
        when(mailLogRepository.countPending()).thenReturn(5L);
        when(mailLogRepository.countSentByTemplateSince(any(Instant.class)))
                .thenReturn(List.of(
                        new Object[]{"order-confirm", 50L},
                        new Object[]{"password-reset", 30L}));

        // Act
        var result = service.stats();

        // Assert
        assertThat(result.totalSent()).isEqualTo(80L);
        assertThat(result.totalFailed()).isEqualTo(20L);
        assertThat(result.totalPending()).isEqualTo(5L);
        assertThat(result.successRate()).isCloseTo(0.8, org.assertj.core.data.Offset.offset(0.001));
        assertThat(result.sentByTemplate()).containsEntry("order-confirm", 50L);
        assertThat(result.sentByTemplate()).containsEntry("password-reset", 30L);
    }

    @Test
    @DisplayName("存在しないIDでget呼び出し時、ResourceNotFoundExceptionをスローする")
    void should_throwResourceNotFoundException_when_getWithInvalidId() {
        // Arrange
        var mailProps = new MailProperties(new MailProperties.Retry(3, 10, 2.0), "http://localhost:3000");
        var meterRegistry = new SimpleMeterRegistry();
        var objectMapper = new ObjectMapper();
        var sleeper = (Sleeper) millis -> {};
        var service = new MailService(
                mailLogRepository, templateService, azureEmailSender,
                eventPublisher, objectMapper, mailProps, meterRegistry, sleeper);

        var unknownId = UUID.randomUUID();
        when(mailLogRepository.findById(unknownId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.get(unknownId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("MailLog")
                .hasMessageContaining(unknownId.toString());
    }

    @Test
    @DisplayName("FAILED以外のステータスでリトライ時、BusinessRuleViolationExceptionをスローする")
    void should_throwBusinessRuleViolation_when_retryNonFailedMail() {
        // Arrange
        var mailProps = new MailProperties(new MailProperties.Retry(3, 10, 2.0), "http://localhost:3000");
        var meterRegistry = new SimpleMeterRegistry();
        var objectMapper = new ObjectMapper();
        var sleeper = (Sleeper) millis -> {};
        var service = new MailService(
                mailLogRepository, templateService, azureEmailSender,
                eventPublisher, objectMapper, mailProps, meterRegistry, sleeper);

        var sentLog = new MailLog("ORDER_CONFIRMED", "e-sent", "c4",
                "user@example.com", null, "order-confirm", "ご注文確認",
                MailStatus.SENT, "{}");

        var logId = sentLog.getId();
        when(mailLogRepository.findById(logId)).thenReturn(Optional.of(sentLog));

        // Act & Assert
        assertThatThrownBy(() -> service.retry(logId))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("FAILED のメールのみリトライ可能です");
    }
}
