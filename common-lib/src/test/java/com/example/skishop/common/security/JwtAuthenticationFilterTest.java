package com.example.skishop.common.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "test-secret-key-for-unit-tests-that-is-long-enough-for-hmac-sha256";
    private final SecretKey signingKey = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    private JwtAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(SECRET);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = mock(FilterChain.class);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("有効なJWTトークンで認証コンテキストが設定される")
    void should_setAuthentication_when_validJwt() throws ServletException, IOException {
        // Arrange
        UUID userId = UUID.randomUUID();
        String token = createToken(userId, "USER", new Date(System.currentTimeMillis() + 60_000));
        request.addHeader("Authorization", "Bearer " + token);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(userId);
        assertThat(authentication.getAuthorities())
                .anyMatch(a -> a.getAuthority().equals("ROLE_USER"));
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("ADMINロールが正しく設定される")
    void should_setAdminRole_when_adminJwt() throws ServletException, IOException {
        // Arrange
        UUID userId = UUID.randomUUID();
        String token = createToken(userId, "ADMIN", new Date(System.currentTimeMillis() + 60_000));
        request.addHeader("Authorization", "Bearer " + token);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities())
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    @Test
    @DisplayName("Authorizationヘッダーなしの場合は認証をスキップする")
    void should_skipAuthentication_when_noAuthorizationHeader() throws ServletException, IOException {
        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Bearer以外のprefixの場合は認証をスキップする")
    void should_skipAuthentication_when_nonBearerPrefix() throws ServletException, IOException {
        // Arrange
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("無効なトークンの場合は認証をスキップする")
    void should_skipAuthentication_when_invalidToken() throws ServletException, IOException {
        // Arrange
        request.addHeader("Authorization", "Bearer invalid.token.here");

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("期限切れトークンの場合は認証をスキップする")
    void should_skipAuthentication_when_expiredToken() throws ServletException, IOException {
        // Arrange
        UUID userId = UUID.randomUUID();
        String token = createToken(userId, "USER", new Date(System.currentTimeMillis() - 60_000));
        request.addHeader("Authorization", "Bearer " + token);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("異なる秘密鍵で署名されたトークンの場合は認証をスキップする")
    void should_skipAuthentication_when_wrongSigningKey() throws ServletException, IOException {
        // Arrange
        SecretKey wrongKey = Keys.hmacShaKeyFor(
                "different-secret-key-that-is-also-long-enough-for-hmac-sha256".getBytes(StandardCharsets.UTF_8));
        UUID userId = UUID.randomUUID();
        String token = Jwts.builder()
                .subject(userId.toString())
                .claim("role", "USER")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(wrongKey)
                .compact();
        request.addHeader("Authorization", "Bearer " + token);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    private String createToken(UUID userId, String role, Date expiration) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("role", role)
                .expiration(expiration)
                .signWith(signingKey)
                .compact();
    }
}
