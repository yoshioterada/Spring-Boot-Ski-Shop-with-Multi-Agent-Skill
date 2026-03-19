package com.example.skishop.mailsend.service;

import com.example.skishop.common.event.DomainEvent;
import com.example.skishop.common.event.EventPublisher;
import com.example.skishop.common.exception.BusinessRuleViolationException;
import com.example.skishop.common.exception.ResourceNotFoundException;
import com.example.skishop.mailsend.config.MailProperties;
import com.example.skishop.mailsend.dto.MailLogResponse;
import com.example.skishop.mailsend.dto.MailStatsResponse;
import com.example.skishop.mailsend.dto.TestMailRequest;
import com.example.skishop.mailsend.model.MailLog;
import com.example.skishop.mailsend.model.MailStatus;
import com.example.skishop.mailsend.repository.MailLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private static final Pattern SIMPLE_EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final MailLogRepository mailLogRepository;
    private final TemplateService templateService;
    private final AzureEmailSender azureEmailSender;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final MailProperties mailProperties;
    private final Sleeper sleeper;

    private final MeterRegistry meterRegistry;
    private final Counter failedCounter;
    private final Counter retryCounter;
    private final Timer sendTimer;

    public MailService(MailLogRepository mailLogRepository,
                       TemplateService templateService,
                       AzureEmailSender azureEmailSender,
                       EventPublisher eventPublisher,
                       ObjectMapper objectMapper,
                       MailProperties mailProperties,
                       MeterRegistry meterRegistry) {
        this(mailLogRepository, templateService, azureEmailSender, eventPublisher,
                objectMapper, mailProperties, meterRegistry, Sleeper.system());
    }

    MailService(MailLogRepository mailLogRepository,
                TemplateService templateService,
                AzureEmailSender azureEmailSender,
                EventPublisher eventPublisher,
                ObjectMapper objectMapper,
                MailProperties mailProperties,
                MeterRegistry meterRegistry,
                Sleeper sleeper) {
        this.mailLogRepository = mailLogRepository;
        this.templateService = templateService;
        this.azureEmailSender = azureEmailSender;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.mailProperties = mailProperties;
        this.sleeper = sleeper;

        this.meterRegistry = meterRegistry;
        this.failedCounter = meterRegistry.counter("mail.failed.total");
        this.retryCounter = meterRegistry.counter("mail.retry.total");
        this.sendTimer = meterRegistry.timer("mail.send.duration");
    }

    @Transactional(readOnly = true)
    public Page<MailLogResponse> list(Pageable pageable) {
        return mailLogRepository.findAllByOrderByCreatedAtDesc(pageable).map(MailLogResponse::from);
    }

    @Transactional(readOnly = true)
    public MailLogResponse get(UUID id) {
        var log = mailLogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MailLog", id.toString()));
        return MailLogResponse.from(log);
    }

    @Transactional
    public MailLogResponse retry(UUID id) {
        var logEntity = mailLogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MailLog", id.toString()));

        if (logEntity.getStatus() != MailStatus.FAILED) {
            throw new BusinessRuleViolationException("INVALID_STATUS", "FAILED のメールのみリトライ可能です");
        }

        Map<String, Object> variables = readVariables(logEntity.getVariablesJson());
        sendUsingExistingLog(logEntity, variables);

        return MailLogResponse.from(logEntity);
    }

    @Transactional
    public MailLogResponse sendTestMail(TestMailRequest request) {
        var variables = new HashMap<>(request.variables());
        String templateName = request.templateName();
        String subject = subjectForTemplate(templateName);

        var eventId = UUID.randomUUID().toString();
        var inbound = new InboundMail(eventId, "TEST", UUID.randomUUID().toString(),
                request.recipientEmail(), null, templateName, subject, variables);

        sendInbound(inbound);

        return mailLogRepository.findByEventId(eventId)
                .map(MailLogResponse::from)
                .orElseThrow();
    }

    @Transactional(readOnly = true)
    public MailStatsResponse stats() {
        long totalSent = mailLogRepository.countSent();
        long totalFailed = mailLogRepository.countFailed();
        long totalPending = mailLogRepository.countPending();
        double successRate = (totalSent + totalFailed) == 0 ? 1.0 : (double) totalSent / (double) (totalSent + totalFailed);

        var since = Instant.now().minus(Duration.ofDays(30));
        var rows = mailLogRepository.countSentByTemplateSince(since);
        Map<String, Long> byTemplate = new HashMap<>();
        for (Object[] row : rows) {
            byTemplate.put((String) row[0], (Long) row[1]);
        }

        return new MailStatsResponse(totalSent, totalFailed, totalPending, successRate, byTemplate);
    }

    @Transactional
    public void sendInbound(InboundMail inbound) {
        meterRegistry.counter("mail.event.consumed.total", "eventType", inbound.eventType()).increment();

        if (mailLogRepository.findByEventId(inbound.eventId()).isPresent()) {
            log.info("Duplicate event skipped: eventId={}, eventType={}", inbound.eventId(), inbound.eventType());
            return;
        }

        var variables = new HashMap<>(inbound.variables());
        variables.putIfAbsent("subject", inbound.subject());
        variables.putIfAbsent("baseUrl", mailProperties.baseUrl());
        if (inbound.recipientName() != null) {
            variables.putIfAbsent("recipientName", inbound.recipientName());
        }

        String variablesJson = writeVariables(variables);

        var logEntity = new MailLog(
                inbound.eventType(),
                inbound.eventId(),
                inbound.correlationId(),
                inbound.recipientEmail(),
                inbound.recipientName(),
                inbound.templateName(),
                inbound.subject(),
                MailStatus.PENDING,
                variablesJson
        );

        try {
            mailLogRepository.save(logEntity);
        } catch (DataIntegrityViolationException e) {
            log.info("Duplicate event insert skipped: eventId={}, eventType={}", inbound.eventId(), inbound.eventType());
            return;
        }

        if (!isValidEmail(inbound.recipientEmail())) {
            log.warn("Invalid recipient email skipped: eventId={}, email={}", inbound.eventId(), inbound.recipientEmail());
            logEntity.markSkipped("Invalid email address");
            mailLogRepository.save(logEntity);
            return;
        }

        sendUsingExistingLog(logEntity, variables);
    }

    private void sendUsingExistingLog(MailLog logEntity, Map<String, Object> variables) {
        sendTimer.record(() -> {
            int maxAttempts = mailProperties.retry().maxAttempts();
            long initial = mailProperties.retry().initialIntervalMs();
            double multiplier = mailProperties.retry().multiplier();

            for (int attempt = 0; attempt < maxAttempts; attempt++) {
                try {
                    logEntity.markSending();
                    mailLogRepository.save(logEntity);

                    var rendered = templateService.render(logEntity.getTemplateName(), variables);
                    String opId = azureEmailSender.send(
                            logEntity.getRecipientEmail(),
                            logEntity.getSubject(),
                            rendered.html(),
                            rendered.plainText());

                    logEntity.markSent(opId);
                    mailLogRepository.save(logEntity);

                    meterRegistry.counter("mail.sent.total", "template", logEntity.getTemplateName()).increment();

                    eventPublisher.publish(DomainEvent.create(
                            "MailSent",
                            "mailsend-service",
                            new MailSentPayload(logEntity.getId(), logEntity.getEventType(), logEntity.getRecipientEmail()),
                            logEntity.getCorrelationId()));

                    return;
                } catch (EmailSendException e) {
                    boolean retryable = e.isRetryable();
                    if (!retryable) {
                        logEntity.markFailed(e.getMessage());
                        mailLogRepository.save(logEntity);
                        failedCounter.increment();
                        publishFailedEvent(logEntity);
                        return;
                    }

                    long delayMs = nextDelayMs(e, initial, multiplier, attempt);
                    log.warn("Send failed (retryable). attempt={}, delayMs={}, eventId={}, statusCode={}",
                            attempt + 1, delayMs, logEntity.getEventId(), e.statusCode());

                    logEntity.incrementRetry();
                    logEntity.markFailed(e.getMessage());
                    mailLogRepository.save(logEntity);
                    retryCounter.increment();

                    if (attempt == maxAttempts - 1) {
                        failedCounter.increment();
                        publishFailedEvent(logEntity);
                        return;
                    }

                    sleep(delayMs);
                } catch (Exception e) {
                    logEntity.incrementRetry();
                    logEntity.markFailed(e.getMessage());
                    mailLogRepository.save(logEntity);
                    retryCounter.increment();

                    if (attempt == maxAttempts - 1) {
                        failedCounter.increment();
                        publishFailedEvent(logEntity);
                        return;
                    }

                    long delayMs = (long) (initial * Math.pow(multiplier, attempt));
                    sleep(delayMs);
                }
            }
        });
    }

    private void publishFailedEvent(MailLog logEntity) {
        eventPublisher.publish(DomainEvent.create(
                "MailSendFailed",
                "mailsend-service",
                new MailSendFailedPayload(logEntity.getId(), logEntity.getEventType(), logEntity.getRecipientEmail(), logEntity.getErrorMessage()),
                logEntity.getCorrelationId()));
    }

    private static long nextDelayMs(EmailSendException e, long initial, double multiplier, int attemptIndex) {
        if (e.statusCode() != null && e.statusCode() == 429 && e.retryAfterMs() != null) {
            return e.retryAfterMs();
        }
        return (long) (initial * Math.pow(multiplier, attemptIndex));
    }

    private void sleep(long delayMs) {
        try {
            sleeper.sleep(delayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Retry interrupted", ex);
        }
    }

    private static boolean isValidEmail(String email) {
        return email != null && SIMPLE_EMAIL_PATTERN.matcher(email).matches();
    }

    private String writeVariables(Map<String, Object> variables) {
        try {
            return objectMapper.writeValueAsString(variables);
        } catch (JsonProcessingException e) {
            throw new BusinessRuleViolationException("INVALID_VARIABLES", "テンプレート変数のシリアライズに失敗しました");
        }
    }

    private Map<String, Object> readVariables(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            throw new BusinessRuleViolationException("INVALID_VARIABLES", "テンプレート変数の読み込みに失敗しました");
        }
    }

    private static String subjectForTemplate(String templateName) {
        return switch (templateName) {
            case "email-verification" -> "メールアドレス確認のお願い";
            case "welcome" -> "Azure SkiShop へようこそ";
            case "password-reset" -> "パスワード再設定のご案内";
            case "order-confirmation" -> "ご注文確認";
            case "order-cancelled" -> "ご注文キャンセル確認";
            case "shipment-notification" -> "発送のお知らせ";
            case "delivery-confirmation" -> "配達完了のお知らせ";
            case "email-change-verification" -> "メールアドレス変更の確認";
            default -> "SkiShop 通知";
        };
    }

    public record InboundMail(
            String eventId,
            String eventType,
            String correlationId,
            String recipientEmail,
            String recipientName,
            String templateName,
            String subject,
            Map<String, Object> variables
    ) {
    }

    public record MailSentPayload(UUID mailLogId, String eventType, String recipientEmail) {
    }

    public record MailSendFailedPayload(UUID mailLogId, String eventType, String recipientEmail, String errorMessage) {
    }
}
