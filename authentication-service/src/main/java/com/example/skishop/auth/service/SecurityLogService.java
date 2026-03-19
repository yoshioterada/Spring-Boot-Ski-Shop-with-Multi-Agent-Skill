package com.example.skishop.auth.service;

import com.example.skishop.auth.model.SecurityLog;
import com.example.skishop.auth.repository.SecurityLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SecurityLogService {

    private static final Logger log = LoggerFactory.getLogger(SecurityLogService.class);

    private final SecurityLogRepository securityLogRepository;

    public SecurityLogService(SecurityLogRepository securityLogRepository) {
        this.securityLogRepository = securityLogRepository;
    }

    @Transactional
    public void logEvent(UUID userId, String eventType, String ipAddress, String userAgent, java.util.Map<String, Object> details) {
        var securityLog = new SecurityLog(userId, eventType, ipAddress, userAgent, details);
        securityLogRepository.save(securityLog);
        log.info("セキュリティイベント記録: eventType={}, userId={}", eventType, userId);
    }

    @Transactional
    public void logLoginSuccess(UUID userId, String ipAddress, String userAgent) {
        logEvent(userId, "LOGIN_SUCCESS", ipAddress, userAgent, null);
    }

    @Transactional
    public void logLoginFailure(String email, String ipAddress, String userAgent, String reason) {
        logEvent(null, "LOGIN_FAILURE", ipAddress, userAgent,
                java.util.Map.of("email", maskEmail(email), "reason", reason));
    }

    @Transactional
    public void logAccountLocked(UUID userId, String ipAddress) {
        logEvent(userId, "ACCOUNT_LOCKED", ipAddress, null, null);
    }

    @Transactional
    public void logPasswordChanged(UUID userId, String ipAddress) {
        logEvent(userId, "PASSWORD_CHANGED", ipAddress, null, null);
    }

    @Transactional
    public void logLogout(UUID userId, String ipAddress) {
        logEvent(userId, "LOGOUT", ipAddress, null, null);
    }

    private static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        String[] parts = email.split("@");
        String local = parts[0];
        return (local.length() <= 2 ? "*" : local.substring(0, 2) + "***") + "@" + parts[1];
    }
}
