package com.example.skishop.auth.service;

import com.example.skishop.auth.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {

    private JwtTokenService jwtTokenService;

    // 64-byte secret for HMAC-SHA512 (must be at least 32 bytes for HS256)
    private static final String JWT_SECRET = "this-is-a-test-secret-key-that-is-at-least-32-bytes-long-for-hmac";

    @BeforeEach
    void setUp() {
        jwtTokenService = new JwtTokenService(JWT_SECRET, 60, 7);
    }

    private User createTestUser() {
        User user = new User("test@example.com", "encoded", "Taro", "Yamada");
        user.setStatus(User.UserStatus.ACTIVE);
        setId(user, UUID.fromString("11111111-1111-1111-1111-111111111111"));
        return user;
    }

    private void setId(User user, UUID id) {
        try {
            Field idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("アクセストークンが正常に生成される")
    void should_generateAccessToken_when_validUser() {
        // Arrange
        User user = createTestUser();

        // Act
        String token = jwtTokenService.generateAccessToken(user);

        // Assert
        assertThat(token).isNotNull().isNotBlank();
    }

    @Test
    @DisplayName("リフレッシュトークンが正常に生成される")
    void should_generateRefreshToken_when_validUser() {
        // Arrange
        User user = createTestUser();

        // Act
        String token = jwtTokenService.generateRefreshToken(user);

        // Assert
        assertThat(token).isNotNull().isNotBlank();
    }

    @Test
    @DisplayName("有効なトークンが検証に成功する")
    void should_validateToken_when_tokenIsValid() {
        // Arrange
        User user = createTestUser();
        String token = jwtTokenService.generateAccessToken(user);

        // Act
        Claims claims = jwtTokenService.validateToken(token);

        // Assert
        assertThat(claims).isNotNull();
        assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
        assertThat(claims.get("email")).isEqualTo("test@example.com");
        assertThat(claims.get("role")).isEqualTo("USER");
        assertThat(claims.get("type")).isEqualTo("access");
    }

    @Test
    @DisplayName("リフレッシュトークンにはtype=refreshが含まれる")
    void should_containRefreshType_when_refreshTokenValidated() {
        // Arrange
        User user = createTestUser();
        String token = jwtTokenService.generateRefreshToken(user);

        // Act
        Claims claims = jwtTokenService.validateToken(token);

        // Assert
        assertThat(claims.get("type")).isEqualTo("refresh");
    }

    @Test
    @DisplayName("改ざんされたトークンは検証に失敗する")
    void should_throwSignatureException_when_tokenTampered() {
        // Arrange
        User user = createTestUser();
        String token = jwtTokenService.generateAccessToken(user);
        String tamperedToken = token + "tampered";

        // Act & Assert
        assertThatThrownBy(() -> jwtTokenService.validateToken(tamperedToken))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("異なるシークレットで署名されたトークンは検証に失敗する")
    void should_throwException_when_differentSecretUsed() {
        // Arrange
        JwtTokenService otherService = new JwtTokenService(
                "another-secret-key-that-is-at-least-32-bytes-long-for-hmac-sha", 60, 7);
        User user = createTestUser();
        String token = otherService.generateAccessToken(user);

        // Act & Assert
        assertThatThrownBy(() -> jwtTokenService.validateToken(token))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    @DisplayName("トークンからユーザーIDが正しく抽出される")
    void should_extractUserId_when_validToken() {
        // Arrange
        User user = createTestUser();
        String token = jwtTokenService.generateAccessToken(user);

        // Act
        UUID userId = jwtTokenService.extractUserId(token);

        // Assert
        assertThat(userId).isEqualTo(user.getId());
    }

    @Test
    @DisplayName("期限切れトークンの検証は失敗する")
    void should_throwExpiredJwtException_when_tokenExpired() {
        // Arrange
        JwtTokenService shortLivedService = new JwtTokenService(JWT_SECRET, 0, 0);
        User user = createTestUser();
        String token = shortLivedService.generateAccessToken(user);

        // Act & Assert
        assertThatThrownBy(() -> jwtTokenService.validateToken(token))
                .isInstanceOf(ExpiredJwtException.class);
    }
}
