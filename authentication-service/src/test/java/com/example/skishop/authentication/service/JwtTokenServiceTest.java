package com.example.skishop.authentication.service;

import com.example.skishop.authentication.config.JwtConfig;
import com.example.skishop.authentication.entity.User;
import com.example.skishop.authentication.exception.InvalidTokenException;
import com.example.skishop.authentication.exception.TokenExpiredException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtTokenServiceTest {

    private static KeyPair keyPair;

    @Mock
    private JwtConfig jwtConfig;

    private JwtTokenService jwtTokenService;
    private User testUser;

    @BeforeAll
    static void generateKeyPair() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keyPair = gen.generateKeyPair();
    }

    @BeforeEach
    void setUp() {
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        RSAKey rsaKey = new RSAKey.Builder(publicKey).privateKey(privateKey).build();
        var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
        var decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();

        // lenient: tests not calling generateAccessToken don't use this stub
        org.mockito.Mockito.lenient().when(jwtConfig.getAccessTokenExpiry()).thenReturn(900L);
        jwtTokenService = new JwtTokenService(encoder, decoder, jwtConfig);

        testUser = new User("testuser", "test@example.com", "hashedpassword");
        testUser.setRoles(Set.of("ROLE_USER"));
        ReflectionTestUtils.setField(testUser, "id", UUID.randomUUID());
    }

    @Test
    @DisplayName("有効なユーザーのアクセストークンを生成できる")
    void should_generateAccessToken_when_validUserProvided() {
        // Act
        String token = jwtTokenService.generateAccessToken(testUser);

        // Assert
        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3); // JWT has 3 parts
    }

    @Test
    @DisplayName("有効なトークンをデコードして JWT を返す")
    void should_returnJwt_when_validTokenProvided() {
        // Arrange
        String token = jwtTokenService.generateAccessToken(testUser);

        // Act
        Jwt jwt = jwtTokenService.validateAndDecodeToken(token);

        // Assert
        assertThat(jwt).isNotNull();
        assertThat(jwt.getSubject()).isEqualTo("testuser");
        assertThat(jwt.getClaim("email").toString()).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("有効なトークンからユーザー名を抽出できる")
    void should_extractUsername_when_validTokenProvided() {
        // Arrange
        String token = jwtTokenService.generateAccessToken(testUser);

        // Act
        String username = jwtTokenService.extractUsername(token);

        // Assert
        assertThat(username).isEqualTo("testuser");
    }

    @Test
    @DisplayName("有効なトークンの場合 isTokenValid が true を返す")
    void should_returnTrue_when_tokenIsValid() {
        // Arrange
        String token = jwtTokenService.generateAccessToken(testUser);

        // Act
        boolean valid = jwtTokenService.isTokenValid(token);

        // Assert
        assertThat(valid).isTrue();
    }

    @Test
    @DisplayName("無効なトークンの場合 isTokenValid が false を返す")
    void should_returnFalse_when_tokenIsInvalid() {
        // Act
        boolean valid = jwtTokenService.isTokenValid("invalid.token.string");

        // Assert
        assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("不正なトークンの場合 InvalidTokenException をスローする")
    void should_throwInvalidTokenException_when_tokenIsMalformed() {
        // Act & Assert
        assertThatThrownBy(() -> jwtTokenService.validateAndDecodeToken("not.a.valid.jwt"))
            .isInstanceOf(InvalidTokenException.class)
            .hasMessageContaining("無効なトークンです");
    }

    @Test
    @DisplayName("ロールが JWT クレームに含まれる")
    void should_includeRoles_when_userHasRoles() {
        // Arrange
        testUser.setRoles(Set.of("ROLE_USER", "ROLE_ADMIN"));
        String token = jwtTokenService.generateAccessToken(testUser);

        // Act
        Jwt jwt = jwtTokenService.validateAndDecodeToken(token);

        // Assert
        assertThat(jwt.getClaimAsStringList("roles"))
            .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    @DisplayName("アクセストークンの有効期限秒数を取得できる")
    void should_returnExpirySeconds_when_getAccessTokenExpirySecondsCalled() {
        // Act
        long expiry = jwtTokenService.getAccessTokenExpirySeconds();

        // Assert
        assertThat(expiry).isEqualTo(900L);
    }
}
