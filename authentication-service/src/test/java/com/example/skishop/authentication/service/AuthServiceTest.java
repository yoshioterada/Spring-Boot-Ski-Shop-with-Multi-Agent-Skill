package com.example.skishop.authentication.service;

import com.example.skishop.authentication.config.AppSecurityProperties;
import com.example.skishop.authentication.config.JwtConfig;
import com.example.skishop.authentication.dto.*;
import com.example.skishop.authentication.entity.User;
import com.example.skishop.authentication.exception.*;
import com.example.skishop.authentication.repository.LoginAttemptRepository;
import com.example.skishop.authentication.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private LoginAttemptRepository loginAttemptRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenService jwtTokenService;
    @Mock
    private TokenStorageService tokenStorageService;
    @Mock
    private AppSecurityProperties securityProperties;
    @Mock
    private JwtConfig jwtConfig;

    @InjectMocks
    private AuthService authService;

    private User testUser;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        testUser = new User("testuser", "test@example.com", "$2a$10$hashedpassword");
        testUser.setRoles(Set.of("ROLE_USER"));
        // Set the id manually since @GeneratedValue only works with a DB
        org.springframework.test.util.ReflectionTestUtils.setField(testUser, "id", userId);
    }

    // --- login ---

    @Test
    @DisplayName("正しい認証情報でログインするとトークンが返る")
    void should_returnTokenResponse_when_validCredentials() {
        // Arrange
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("password123", testUser.getPasswordHash())).thenReturn(true);
        when(loginAttemptRepository.save(any())).thenReturn(null);
        when(jwtTokenService.generateAccessToken(testUser)).thenReturn("access.token.value");
        when(tokenStorageService.generateRefreshToken()).thenReturn("refreshtoken");
        when(jwtConfig.getRefreshTokenExpiry()).thenReturn(86400L);
        when(jwtTokenService.getAccessTokenExpirySeconds()).thenReturn(900L);

        // Act
        TokenResponse result = authService.login(
            new LoginRequest("testuser", "password123"), "127.0.0.1", "TestAgent");

        // Assert
        assertThat(result.accessToken()).isEqualTo("access.token.value");
        assertThat(result.refreshToken()).isEqualTo("refreshtoken");
        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(result.expiresIn()).isEqualTo(900L);
        verify(tokenStorageService).storeRefreshToken(eq("refreshtoken"), any(), any(Duration.class));
    }

    @Test
    @DisplayName("存在しないユーザー名でログインすると AuthenticationException をスローする")
    void should_throwAuthenticationException_when_userNotFound() {
        // Arrange
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());
        when(loginAttemptRepository.save(any())).thenReturn(null);

        // Act & Assert
        assertThatThrownBy(() ->
            authService.login(new LoginRequest("unknown", "password123"), "127.0.0.1", "TestAgent"))
            .isInstanceOf(AuthenticationException.class)
            .hasMessageContaining("ユーザー名またはパスワードが正しくありません");
    }

    @Test
    @DisplayName("パスワードが間違っている場合 AuthenticationException をスローする")
    void should_throwAuthenticationException_when_wrongPassword() {
        // Arrange
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(securityProperties.getMaxLoginAttempts()).thenReturn(5);
        when(passwordEncoder.matches("wrongpassword", testUser.getPasswordHash())).thenReturn(false);
        when(loginAttemptRepository.save(any())).thenReturn(null);
        when(userRepository.save(any())).thenReturn(testUser);

        // Act & Assert
        assertThatThrownBy(() ->
            authService.login(new LoginRequest("testuser", "wrongpassword"), "127.0.0.1", "TestAgent"))
            .isInstanceOf(AuthenticationException.class)
            .hasMessageContaining("ユーザー名またはパスワードが正しくありません");
    }

    @Test
    @DisplayName("ログイン失敗を maxLoginAttempts 回繰り返すとアカウントがロックされる")
    void should_lockAccount_when_maxFailedAttemptsReached() {
        // Arrange
        testUser.setFailedAttempts(4); // one more will hit max
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(securityProperties.getMaxLoginAttempts()).thenReturn(5);
        when(passwordEncoder.matches(any(), any())).thenReturn(false);
        when(loginAttemptRepository.save(any())).thenReturn(null);
        when(userRepository.save(any())).thenReturn(testUser);

        // Act
        assertThatThrownBy(() ->
            authService.login(new LoginRequest("testuser", "wrong"), "127.0.0.1", "TestAgent"))
            .isInstanceOf(AuthenticationException.class);

        // Assert
        assertThat(testUser.isLocked()).isTrue();
        assertThat(testUser.getFailedAttempts()).isEqualTo(5);
    }

    @Test
    @DisplayName("ロックされたアカウントでログインすると AccountLockedException をスローする")
    void should_throwAccountLockedException_when_accountIsLocked() {
        // Arrange
        testUser.setLocked(true);
        testUser.setLockTime(LocalDateTime.now().minusMinutes(5)); // locked 5 min ago, not expired yet
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(securityProperties.getAccountLockDuration()).thenReturn(Duration.ofMinutes(15));

        // Act & Assert
        assertThatThrownBy(() ->
            authService.login(new LoginRequest("testuser", "password123"), "127.0.0.1", "TestAgent"))
            .isInstanceOf(AccountLockedException.class)
            .hasMessageContaining("アカウントがロックされています");
    }

    // --- register ---

    @Test
    @DisplayName("新規ユーザーを登録するとユーザー情報が返る")
    void should_returnUserInfoResponse_when_registerSucceeds() {
        // Arrange
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$encoded");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(u, "id", UUID.randomUUID());
            return u;
        });

        // Act
        UserInfoResponse result = authService.register(
            new RegisterRequest("newuser", "new@example.com", "password123"));

        // Assert
        assertThat(result.username()).isEqualTo("newuser");
        assertThat(result.email()).isEqualTo("new@example.com");
        assertThat(result.enabled()).isTrue();
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("重複したユーザー名で登録すると UserAlreadyExistsException をスローする")
    void should_throwUserAlreadyExistsException_when_usernameAlreadyExists() {
        // Arrange
        when(userRepository.existsByUsername("testuser")).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() ->
            authService.register(new RegisterRequest("testuser", "new@example.com", "password123")))
            .isInstanceOf(UserAlreadyExistsException.class)
            .hasMessageContaining("testuser");
    }

    @Test
    @DisplayName("重複したメールアドレスで登録すると UserAlreadyExistsException をスローする")
    void should_throwUserAlreadyExistsException_when_emailAlreadyExists() {
        // Arrange
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() ->
            authService.register(new RegisterRequest("newuser", "test@example.com", "password123")))
            .isInstanceOf(UserAlreadyExistsException.class)
            .hasMessageContaining("test@example.com");
    }

    // --- refreshToken ---

    @Test
    @DisplayName("有効なリフレッシュトークンで新しいトークンが返る")
    void should_returnNewTokens_when_refreshTokenIsValid() {
        // Arrange
        String refreshToken = "valid-refresh-token";
        when(tokenStorageService.getUserIdByRefreshToken(refreshToken))
            .thenReturn(Optional.of(userId.toString()));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(jwtTokenService.generateAccessToken(testUser)).thenReturn("new.access.token");
        when(tokenStorageService.generateRefreshToken()).thenReturn("new-refresh-token");
        when(jwtConfig.getRefreshTokenExpiry()).thenReturn(86400L);
        when(jwtTokenService.getAccessTokenExpirySeconds()).thenReturn(900L);

        // Act
        TokenResponse result = authService.refreshToken(new RefreshTokenRequest(refreshToken));

        // Assert
        assertThat(result.accessToken()).isEqualTo("new.access.token");
        assertThat(result.refreshToken()).isEqualTo("new-refresh-token");
        verify(tokenStorageService).deleteRefreshToken(refreshToken);
        verify(tokenStorageService).storeRefreshToken(eq("new-refresh-token"), any(), any(Duration.class));
    }

    @Test
    @DisplayName("無効なリフレッシュトークンで InvalidTokenException をスローする")
    void should_throwInvalidTokenException_when_refreshTokenIsInvalid() {
        // Arrange
        when(tokenStorageService.getUserIdByRefreshToken("invalid-token"))
            .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() ->
            authService.refreshToken(new RefreshTokenRequest("invalid-token")))
            .isInstanceOf(InvalidTokenException.class);
    }

    // --- logout ---

    @Test
    @DisplayName("ログアウト時にリフレッシュトークンが削除される")
    void should_deleteRefreshToken_when_logoutCalled() {
        // Act
        authService.logout("some-refresh-token");

        // Assert
        verify(tokenStorageService).deleteRefreshToken("some-refresh-token");
    }

    @Test
    @DisplayName("null リフレッシュトークンでログアウトしても例外が発生しない")
    void should_notThrow_when_logoutWithNullRefreshToken() {
        // Act & Assert
        assertThatCode(() -> authService.logout(null))
            .doesNotThrowAnyException();
        verify(tokenStorageService, never()).deleteRefreshToken(any());
    }

    // --- validateToken ---

    @Test
    @DisplayName("有効なトークンの場合 valid=true のレスポンスを返す")
    void should_returnValidResponse_when_tokenIsValid() {
        // Arrange
        String token = "valid.jwt.token";
        Jwt mockJwt = mock(Jwt.class);
        when(jwtTokenService.isTokenValid(token)).thenReturn(true);
        when(jwtTokenService.validateAndDecodeToken(token)).thenReturn(mockJwt);
        when(mockJwt.getSubject()).thenReturn("testuser");
        when(mockJwt.getClaimAsStringList("roles")).thenReturn(List.of("ROLE_USER"));
        when(mockJwt.getExpiresAt()).thenReturn(Instant.now().plusSeconds(900));

        // Act
        TokenValidationResponse result = authService.validateToken(token);

        // Assert
        assertThat(result.valid()).isTrue();
        assertThat(result.subject()).isEqualTo("testuser");
        assertThat(result.scopes()).contains("ROLE_USER");
    }

    @Test
    @DisplayName("無効なトークンの場合 valid=false のレスポンスを返す")
    void should_returnInvalidResponse_when_tokenIsInvalid() {
        // Arrange
        when(jwtTokenService.isTokenValid("bad.token")).thenReturn(false);

        // Act
        TokenValidationResponse result = authService.validateToken("bad.token");

        // Assert
        assertThat(result.valid()).isFalse();
        assertThat(result.subject()).isNull();
        assertThat(result.scopes()).isEmpty();
    }
}
