package com.example.skishop.ai.service;

import com.example.skishop.ai.model.LlmAuditLog;
import com.example.skishop.ai.repository.LlmAuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * LLM 監査ログ記録サービス (D-COM-08).
 * プロンプト原文は保存せず SHA-256 ハッシュのみ。
 */
@Service
public class LlmAuditService {

    private static final Logger log = LoggerFactory.getLogger(LlmAuditService.class);
    private static final long TTL_DAYS = 365;

    private final LlmAuditLogRepository repository;

    public LlmAuditService(LlmAuditLogRepository repository) {
        this.repository = repository;
    }

    public void record(String featureId, String promptHash, String endpoint,
                       int inputTokens, int outputTokens, long durationMs,
                       boolean success, String errorMessage) {
        var auditLog = new LlmAuditLog();
        auditLog.setTraceId(MDC.get("traceId"));
        auditLog.setUserId(MDC.get("userId"));
        auditLog.setFeatureId(featureId);
        auditLog.setPromptHash(promptHash);
        auditLog.setEndpoint(endpoint);
        auditLog.setInputTokenCount(inputTokens);
        auditLog.setOutputTokenCount(outputTokens);
        auditLog.setDurationMs(durationMs);
        auditLog.setSuccess(success);
        auditLog.setErrorMessage(errorMessage);
        auditLog.setTtl(Instant.now().plus(TTL_DAYS, ChronoUnit.DAYS));

        try {
            repository.save(auditLog);
            log.info("[LlmAudit] feature={} success={} tokens={}/{} durationMs={}",
                    featureId, success, inputTokens, outputTokens, durationMs);
        } catch (Exception e) {
            log.error("Failed to save LLM audit log: {}", e.getMessage());
        }
    }
}
