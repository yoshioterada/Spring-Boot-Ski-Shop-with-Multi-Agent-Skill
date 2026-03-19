package com.example.skishop.auth.service;

import com.example.skishop.auth.dto.LoginRequest;
import com.example.skishop.auth.dto.PasswordChangeRequest;
import com.example.skishop.auth.dto.PasswordResetConfirmRequest;
import com.example.skishop.auth.dto.PasswordResetRequest;
import com.example.skishop.auth.dto.RegisterRequest;
import com.example.skishop.auth.dto.TokenRefreshRequest;
import com.example.skishop.auth.model.PasswordReset;
import com.example.skishop.auth.model.User;
import com.example.skishop.auth.repository.PasswordResetRepository;
import com.example.skishop.auth.repository.UserRepository;
import com.example.skishop.auth.repository.UserSessionRepository;
import com.example.skishop.common.event.EventPublisher;
import com.example.skishop.common.exception.AuthenticationFailedException;
import com.example.skishop.common.exception.BusinessRuleViolationException;
import com.example.skishop.common.exception.ResourceNotFoundException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserSessionRepository userSessionRepository;
    @Mock
    private PasswordResetRepository passwordResetRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenService jwtTokenService;
    @Mock
    private EventPublisher eventPublisher;
    @Mock
    private SecurityLogService securityLogService;

    private AuthenticationService authenticationService;

    private static final UUID TEST_USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @BeforeEach
    void setUp() {
        authenticationService = new AuthenticationService(
                userRepository, userSessionRepository, passwordResetRepository,
                passwordEncoder, jwtTokenService, eventPublisher, securityLogService);
    }

    private User createActiveUser() {
        var user = new User("test@example.com", "encoded", "Taro", "Yamada");
        user.setStatus(User.UserStatus.ACTIVE);
        setUserId(user, TEST_USER_ID);
        return user;
    }

    private void setUserId(User user, UUID id) {
        try {
            Field idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    @DisplayName("register - ユーザー登録")
    class RegisterTests {

        @Test
        @DisplayName("有効な情報でユーザー登録が成功する")
        void should_registerUser_when_validRequestProvided() {
            // Arrange
            var request = new RegisterRequest("test@example.com", "Password1", "Taro", "Yamada");
            when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
            when(passwordEncoder.encode("Password1")).thenReturn("encoded");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(jwtTokenService.generateAccessToken(any())).thenReturn("access-token");
            when(jwtTokenService.generateRefreshToken(any())).thenReturn("refresh-token");

            // Act
            var response = authenticationService.register(request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.email()).isEqualTo("test@example.com");
            assertThat(response.accessToken()).isEqualTo("access-token");
            verify(eventPublisher).publish(any());
        }

        @Test
        @DisplayName("既存メールアドレスで登録した場合、BusinessRuleViolationExceptionがスローされる")
        void should_throwBusinessRuleViolation_when_emailAlreadyExists() {
            // Arrange
            var request = new RegisterRequest("existing@example.com", "Password1", "Taro", "Yamada");
            when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> authenticationService.register(request))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("既に登録されています");
        }
    }

    @Nested
    @DisplayName("login - ログイン")
    class LoginTests {

        @Test
        @DisplayName("正しい認証情報でログインが成功する")
        void should_loginSuccessfully_when_validCredentials() {
            // Arrange
            var request = new LoginRequest("test@example.com", "Password1");
            var user = createActiveUser();

            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("Password1", "encoded")).thenReturn(true);
            when(userRepository.save(any(User.class))).thenReturn(user);
            when(jwtTokenService.generateAccessToken(any())).thenReturn("access-token");
            when(jwtTokenService.generateRefreshToken(any())).thenReturn("refresh-token");

            // Act
            var response = authenticationService.login(request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.accessToken()).isEqualTo("access-token");
            verify(securityLogService).logLoginSuccess(any(), any(), any());
        }

        @Test
        @DisplayName("存在しないメールアドレスでログインした場合、AuthenticationFailedExceptionがスローされる")
        void should_throwAuthenticationFailed_when_emailNotFound() {
            // Arrange
            var request = new LoginRequest("unknown@example.com", "Password1");
            when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> authenticationService.login(request))
                    .isInstanceOf(AuthenticationFailedException.class);
        }

        @Test
        @DisplayName("ロックされたアカウントでログインした場合、AuthenticationFailedExceptionがスローされる")
        void should_throwAuthenticationFailed_when_accountLocked() {
            // Arrange
            var request = new LoginRequest("locked@example.com", "Password1");
            var user = new User("locked@example.com", "encoded", "Taro", "Yamada");
            user.setAccountLocked(true);
            when(userRepository.findByEmail("locked@example.com")).thenReturn(Optional.of(user));

            // Act & Assert
            assertThatThrownBy(() -> authenticationService.login(request))
                    .isInstanceOf(AuthenticationFailedException.class)
                    .hasMessageContaining("ロック");
        }

        @Test
        @DisplayName("無効化されたアカウントでログインした場合、AuthenticationFailedExceptionがスローされる")
        void should_throwAuthenticationFailed_when_accountInactive() {
            // Arrange
            var request = new LoginRequest("inactive@example.com", "Password1");
            var user = new User("inactive@example.com", "encoded", "Taro", "Yamada");
            user.setActive(false);
            when(userRepository.findByEmail("inactive@example.com")).thenReturn(Optional.of(user));

            // Act & Assert
            assertThatThrownBy(() -> authenticationService.login(request))
                    .isInstanceOf(AuthenticationFailedException.class)
                    .hasMessageContaining("無効化");
        }

        @Test
        @DisplayName("パスワードが不正な場合、失敗回数がインクリメントされる")
        void should_incrementFailedAttempts_when_wrongPassword() {
            // Arrange
            var request = new LoginRequest("test@example.com", "WrongPassword");
            var user = createActiveUser();
            user.setFailedLoginAttempts(0);

            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("WrongPassword", "encoded")).thenReturn(false);

            // Act & Assert
            assertThatThrownBy(() -> authenticationService.login(request))
                    .isInstanceOf(AuthenticationFailedException.class);
            assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("5回連続失敗でアカウントがロックされる")
        void should_lockAccount_when_maxFailedAttemptsReached() {
            // Arrange
            var request = new LoginRequest("test@example.com", "WrongPassword");
            var user = createActiveUser();
            user.setFailedLoginAttempts(4);

            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("WrongPassword", "encoded")).thenReturn(false);

            // Act & Assert
            assertThatThrownBy(() -> authenticationService.login(request))
                    .isInstanceOf(AuthenticationFailedException.class);
            assertThat(user.isAccountLocked()).isTrue();
            verify(securityLogService).logAccountLocked(any(), any());
            verify(eventPublisher).publish(any());
        }
    }

    @Nested
    @DisplayName("refreshToken - トークンリフレッシュ")
    class RefreshTokenTests {

        @Test
        @DisplayName("有効なリフレッシュトークンで新しいトークンが発行される")
        void should_issueNewTokens_when_validRefreshToken() {
            // Arrange
            var request = new TokenRefreshRequest("valid-refresh-token");
            var claims = createClaims(TEST_USER_ID.toString(), "refresh");
            var user = createActiveUser();

            when(jwtTokenService.validateToken("valid-refresh-token")).thenReturn(claims);
            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
            when(jwtTokenService.generateAccessToken(user)).thenReturn("new-access-token");
            when(jwtTokenService.generateRefreshToken(user)).thenReturn("new-refresh-token");

            // Act
            var response = authenticationService.refreshToken(request);

            // Assert
            assertThat(response.accessToken()).isEqualTo("new-access-token");
            assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
        }

        @Test
        @DisplayName("アクセストークンでリフレッシュすると失敗する")
        void should_throwException_when_accessTokenUsedForRefresh() {
            // Arrange
            var request = new TokenRefreshRequest("access-token");
            var claims = createClaims(TEST_USER_ID.toString(), "access");

            when(jwtTokenService.validateToken("access-token")).thenReturn(claims);

            // Act & Assert
            assertThatThrownBy(() -> authenticationService.refreshToken(request))
                    .isInstanceOf(AuthenticationFailedException.class)
                    .hasMessageContaining("リフレッシュトークン");
        }

        @Test
        @DisplayName("無効なトークンでリフレッシュすると失敗する")
        void should_throwException_when_invalidRefreshToken() {
            // Arrange
            var request = new TokenRefreshRequest("invalid-token");
            when(jwtTokenService.validateToken("invalid-token")).thenThrow(new JwtException("Invalid"));

            // Act & Assert
            assertThatThrownBy(() -> authenticationService.refreshToken(request))
                    .isInstanceOf(AuthenticationFailedException.class);
        }
    }

    @Nested
    @DisplayName("logout - ログアウト")
    class LogoutTests {

        @Test
        @DisplayName("ログアウトでセッションが無効化される")
        void should_deactivateSessions_when_logout() {
            // Act
            var response = authenticationService.logout(TEST_USER_ID, "127.0.0.1");

            // Assert
            assertThat(response.message()).contains("ログアウト");
            verify(userSessionRepository).deactivateAllByUserId(TEST_USER_ID);
            verify(securityLogService).logLogout(TEST_USER_ID, "127.0.0.1");
            verify(eventPublisher).publish(any());
        }
    }

    @Nested
    @DisplayName("validateToken - トークン検証")
    class ValidateTokenTests {

        @Test
        @DisplayName("有効なアクセストークンで検証が成功する")
        void should_returnValid_when_validAccessToken() {
            // Arrange
            var claims = createClaims(TEST_USER_ID.toString(), "access");
            lenient().when(claims.get("email", String.class)).thenReturn("test@example.com");
            lenient().when(claims.get("role", String.class)).thenReturn("USER");
            when(claims.getExpiration()).thenReturn(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)));

            when(jwtTokenService.validateToken("valid-token")).thenReturn(claims);

            // Act
            var response = authenticationService.validateToken("valid-token");

            // Assert
            assertThat(response.valid()).isTrue();
            assertThat(response.userId()).isEqualTo(TEST_USER_ID.toString());
            assertThat(response.email()).isEqualTo("test@example.com");
        }

        @Test
        @DisplayName("リフレッシュトークンでの検証は無効を返す")
        void should_returnInvalid_when_refreshTokenUsedForValidation() {
            // Arrange
            var claims = createClaims(TEST_USER_ID.toString(), "refresh");
            when(jwtTokenService.validateToken("refresh-token")).thenReturn(claims);

            // Act
            var response = authenticationService.validateToken("refresh-token");

            // Assert
            assertThat(response.valid()).isFalse();
        }

        @Test
        @DisplayName("無効なトークンでの検証は無効を返す")
        void should_returnInvalid_when_invalidToken() {
            // Arrange
            when(jwtTokenService.validateToken("invalid-token")).thenThrow(new JwtException("Invalid"));

            // Act
            var response = authenticationService.validateToken("invalid-token");

            // Assert
            assertThat(response.valid()).isFalse();
        }
    }

    @Nested
    @DisplayName("getCurrentUser - 現在のユーザー情報取得")
    class GetCurrentUserTests {

        @Test
        @DisplayName("存在するユーザーの情報を取得できる")
        void should_returnUserInfo_when_validUserId() {
            // Arrange
            var user = createActiveUser();
            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));

            // Act
            var response = authenticationService.getCurrentUser(TEST_USER_ID);

            // Assert
            assertThat(response.id()).isEqualTo(TEST_USER_ID);
            assertThat(response.email()).isEqualTo("test@example.com");
            assertThat(response.firstName()).isEqualTo("Taro");
            assertThat(response.lastName()).isEqualTo("Yamada");
        }

        @Test
        @DisplayName("存在しないユーザーの場合ResourceNotFoundExceptionがスローされる")
        void should_throwNotFound_when_userIdNotExists() {
            // Arrange
            UUID unknownId = UUID.randomUUID();
            when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> authenticationService.getCurrentUser(unknownId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("requestPasswordReset - パスワードリセット要求")
    class PasswordResetRequestTests {

        @Test
        @DisplayName("存在するメールアドレスでリセットトークンが生成される")
        void should_generateResetToken_when_emailExists() {
            // Arrange
            var request = new PasswordResetRequest("test@example.com");
            var user = createActiveUser();
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

            // Act
            var response = authenticationService.requestPasswordReset(request);

            // Assert
            assertThat(response.message()).contains("パスワードリセット");
            verify(passwordResetRepository).save(any(PasswordReset.class));
            verify(eventPublisher).publish(any());
        }

        @Test
        @DisplayName("存在しないメールアドレスでも同じレスポンスが返される（情報漏洩防止）")
        void should_returnSameResponse_when_emailNotExists() {
            // Arrange
            var request = new PasswordResetRequest("unknown@example.com");
            when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

            // Act
            var response = authenticationService.requestPasswordReset(request);

            // Assert
            assertThat(response.message()).contains("パスワードリセット");
            verify(passwordResetRepository, never()).save(any());
            verify(eventPublisher, never()).publish(any());
        }
    }

    @Nested
    @DisplayName("confirmPasswordReset - パスワードリセット確認")
    class PasswordResetConfirmTests {

        @Test
        @DisplayName("有効なトークンでパスワードがリセットされる")
        void should_resetPassword_when_validToken() {
            // Arrange
            var request = new PasswordResetConfirmRequest("valid-token", "NewPassword1");
            var resetEntry = new PasswordReset(TEST_USER_ID, "valid-token",
                    Instant.now().plus(1, ChronoUnit.HOURS));
            var user = createActiveUser();

            when(passwordResetRepository.findByTokenAndUsedFalse("valid-token"))
                    .thenReturn(Optional.of(resetEntry));
            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
            when(passwordEncoder.encode("NewPassword1")).thenReturn("new-encoded");

            // Act
            var response = authenticationService.confirmPasswordReset(request);

            // Assert
            assertThat(response.message()).contains("リセット");
            assertThat(user.getPasswordHash()).isEqualTo("new-encoded");
            assertThat(resetEntry.isUsed()).isTrue();
            verify(eventPublisher).publish(any());
        }

        @Test
        @DisplayName("無効なトークンでリセットすると失敗する")
        void should_throwException_when_invalidResetToken() {
            // Arrange
            var request = new PasswordResetConfirmRequest("invalid-token", "NewPassword1");
            when(passwordResetRepository.findByTokenAndUsedFalse("invalid-token"))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> authenticationService.confirmPasswordReset(request))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        @DisplayName("期限切れトークンでリセットすると失敗する")
        void should_throwException_when_expiredResetToken() {
            // Arrange
            var request = new PasswordResetConfirmRequest("expired-token", "NewPassword1");
            var resetEntry = new PasswordReset(TEST_USER_ID, "expired-token",
                    Instant.now().minus(1, ChronoUnit.HOURS));

            when(passwordResetRepository.findByTokenAndUsedFalse("expired-token"))
                    .thenReturn(Optional.of(resetEntry));

            // Act & Assert
            assertThatThrownBy(() -> authenticationService.confirmPasswordReset(request))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("期限切れ");
        }
    }

    @Nested
    @DisplayName("changePassword - パスワード変更")
    class PasswordChangeTests {

        @Test
        @DisplayName("正しい現在のパスワードで変更が成功する")
        void should_changePassword_when_currentPasswordCorrect() {
            // Arrange
            var request = new PasswordChangeRequest("OldPassword1", "NewPassword1");
            var user = createActiveUser();

            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("OldPassword1", "encoded")).thenReturn(true);
            when(passwordEncoder.encode("NewPassword1")).thenReturn("new-encoded");

            // Act
            var response = authenticationService.changePassword(TEST_USER_ID, request);

            // Assert
            assertThat(response.message()).contains("変更");
            assertThat(user.getPasswordHash()).isEqualTo("new-encoded");
            verify(eventPublisher).publish(any());
        }

        @Test
        @DisplayName("現在のパスワードが不正な場合、AuthenticationFailedExceptionがスローされる")
        void should_throwException_when_currentPasswordWrong() {
            // Arrange
            var request = new PasswordChangeRequest("WrongPassword", "NewPassword1");
            var user = createActiveUser();

            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("WrongPassword", "encoded")).thenReturn(false);

            // Act & Assert
            assertThatThrownBy(() -> authenticationService.changePassword(TEST_USER_ID, request))
                    .isInstanceOf(AuthenticationFailedException.class)
                    .hasMessageContaining("パスワード");
        }
    }

    @Nested
    @DisplayName("softDeleteUser - ユーザー無効化")
    class SoftDeleteTests {

        @Test
        @DisplayName("ユーザーが正常に無効化される")
        void should_deactivateUser_when_softDelete() {
            // Arrange
            var user = createActiveUser();
            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));

            // Act
            var response = authenticationService.softDeleteUser(TEST_USER_ID, "テスト削除");

            // Assert
            assertThat(response.message()).contains("無効化");
            assertThat(user.isActive()).isFalse();
            assertThat(user.getStatus()).isEqualTo(User.UserStatus.DEACTIVATED);
            verify(userSessionRepository).deactivateAllByUserId(TEST_USER_ID);
            verify(eventPublisher).publish(any());
        }

        @Test
        @DisplayName("存在しないユーザーの場合ResourceNotFoundExceptionがスローされる")
        void should_throwNotFound_when_userNotExistsForSoftDelete() {
            // Arrange
            UUID unknownId = UUID.randomUUID();
            when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> authenticationService.softDeleteUser(unknownId, "削除"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("hardDeleteUser - ユーザー完全削除")
    class HardDeleteTests {

        @Test
        @DisplayName("ユーザーが完全に削除される")
        void should_deleteUser_when_hardDelete() {
            // Arrange
            var user = createActiveUser();
            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));

            // Act
            var response = authenticationService.hardDeleteUser(TEST_USER_ID, "管理者削除");

            // Assert
            assertThat(response.message()).contains("完全に削除");
            verify(userRepository).delete(user);
            verify(userSessionRepository).deactivateAllByUserId(TEST_USER_ID);
            verify(eventPublisher).publish(any());
        }
    }

    @Nested
    @DisplayName("findById - ユーザー検索")
    class FindByIdTests {

        @Test
        @DisplayName("存在するユーザーIDでfindByIdが成功する")
        void should_returnUser_when_validUserId() {
            // Arrange
            var user = createActiveUser();
            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));

            // Act
            var result = authenticationService.findById(TEST_USER_ID);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getEmail()).isEqualTo("test@example.com");
        }

        @Test
        @DisplayName("存在しないユーザーIDでfindById時にResourceNotFoundExceptionがスローされる")
        void should_throwNotFound_when_userIdNotExists() {
            // Arrange
            UUID userId = UUID.randomUUID();
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> authenticationService.findById(userId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    private Claims createClaims(String subject, String type) {
        Claims claims = mock(Claims.class);
        lenient().when(claims.getSubject()).thenReturn(subject);
        lenient().when(claims.get("type", String.class)).thenReturn(type);
        return claims;
    }
}
