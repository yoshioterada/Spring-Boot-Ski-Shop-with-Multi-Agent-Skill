package com.example.skishop.auth.service;

import com.example.skishop.auth.model.SecurityLog;
import com.example.skishop.auth.repository.SecurityLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SecurityLogServiceTest {

    @Mock
    private SecurityLogRepository securityLogRepository;

    private SecurityLogService securityLogService;

    private static final UUID TEST_USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @BeforeEach
    void setUp() {
        securityLogService = new SecurityLogService(securityLogRepository);
    }

    @Test
    @DisplayName("ログイン成功イベントが記録される")
    void should_logLoginSuccess_when_called() {
        // Act
        securityLogService.logLoginSuccess(TEST_USER_ID, "192.168.1.1", "Mozilla/5.0");

        // Assert
        var captor = ArgumentCaptor.forClass(SecurityLog.class);
        verify(securityLogRepository).save(captor.capture());
        var savedLog = captor.getValue();
        assertThat(savedLog.getUserId()).isEqualTo(TEST_USER_ID);
        assertThat(savedLog.getEventType()).isEqualTo("LOGIN_SUCCESS");
        assertThat(savedLog.getIpAddress()).isEqualTo("192.168.1.1");
        assertThat(savedLog.getUserAgent()).isEqualTo("Mozilla/5.0");
    }

    @Test
    @DisplayName("ログイン失敗イベントが記録される")
    void should_logLoginFailure_when_called() {
        // Act
        securityLogService.logLoginFailure("test@example.com", "192.168.1.1", "Mozilla/5.0", "Invalid password");

        // Assert
        var captor = ArgumentCaptor.forClass(SecurityLog.class);
        verify(securityLogRepository).save(captor.capture());
        var savedLog = captor.getValue();
        assertThat(savedLog.getEventType()).isEqualTo("LOGIN_FAILURE");
        assertThat(savedLog.getUserId()).isNull();
        assertThat(savedLog.getDetails())
                .isNotNull()
                .containsEntry("reason", "Invalid password");
    }

    @Test
    @DisplayName("アカウントロックイベントが記録される")
    void should_logAccountLocked_when_called() {
        // Act
        securityLogService.logAccountLocked(TEST_USER_ID, "192.168.1.1");

        // Assert
        var captor = ArgumentCaptor.forClass(SecurityLog.class);
        verify(securityLogRepository).save(captor.capture());
        var savedLog = captor.getValue();
        assertThat(savedLog.getUserId()).isEqualTo(TEST_USER_ID);
        assertThat(savedLog.getEventType()).isEqualTo("ACCOUNT_LOCKED");
    }

    @Test
    @DisplayName("パスワード変更イベントが記録される")
    void should_logPasswordChanged_when_called() {
        // Act
        securityLogService.logPasswordChanged(TEST_USER_ID, "192.168.1.1");

        // Assert
        var captor = ArgumentCaptor.forClass(SecurityLog.class);
        verify(securityLogRepository).save(captor.capture());
        var savedLog = captor.getValue();
        assertThat(savedLog.getUserId()).isEqualTo(TEST_USER_ID);
        assertThat(savedLog.getEventType()).isEqualTo("PASSWORD_CHANGED");
    }

    @Test
    @DisplayName("ログアウトイベントが記録される")
    void should_logLogout_when_called() {
        // Act
        securityLogService.logLogout(TEST_USER_ID, "192.168.1.1");

        // Assert
        var captor = ArgumentCaptor.forClass(SecurityLog.class);
        verify(securityLogRepository).save(captor.capture());
        var savedLog = captor.getValue();
        assertThat(savedLog.getUserId()).isEqualTo(TEST_USER_ID);
        assertThat(savedLog.getEventType()).isEqualTo("LOGOUT");
    }

    @Test
    @DisplayName("メールアドレスがマスキングされてログに記録される")
    void should_maskEmail_when_loggingLoginFailure() {
        // Act
        securityLogService.logLoginFailure("user@example.com", "192.168.1.1", null, "Not found");

        // Assert
        var captor = ArgumentCaptor.forClass(SecurityLog.class);
        verify(securityLogRepository).save(captor.capture());
        var savedLog = captor.getValue();
        assertThat(savedLog.getDetails())
                .isNotNull()
                .containsEntry("email", "us***@example.com");
        assertThat(savedLog.getDetails().get("email")).isNotEqualTo("user@example.com");
    }
}
