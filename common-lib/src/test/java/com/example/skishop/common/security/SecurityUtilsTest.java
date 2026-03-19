package com.example.skishop.common.security;

import com.example.skishop.common.exception.AuthorizationDeniedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityUtilsTest {

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // --- getCurrentUserId ---

    @Test
    @DisplayName("認証済みユーザーのIDを取得できる")
    void should_returnUserId_when_authenticated() {
        // Arrange
        setAuthentication(userId, "USER");

        // Act & Assert
        assertThat(SecurityUtils.getCurrentUserId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("未認証の場合はIllegalStateExceptionをスローする")
    void should_throwException_when_notAuthenticated() {
        assertThatThrownBy(SecurityUtils::getCurrentUserId)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("principalがnullの場合はIllegalStateExceptionをスローする")
    void should_throwException_when_principalNull() {
        // Arrange
        var auth = new UsernamePasswordAuthenticationToken(null, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        // Act & Assert
        assertThatThrownBy(SecurityUtils::getCurrentUserId)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("principalがUUID以外の場合はIllegalStateExceptionをスローする")
    void should_throwException_when_principalNotUuid() {
        // Arrange
        var auth = new UsernamePasswordAuthenticationToken("string-principal", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        // Act & Assert
        assertThatThrownBy(SecurityUtils::getCurrentUserId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unexpected principal type");
    }

    // --- isCurrentUser ---

    @Test
    @DisplayName("同一ユーザーの場合はtrueを返す")
    void should_returnTrue_when_sameUser() {
        // Arrange
        setAuthentication(userId, "USER");

        // Act & Assert
        assertThat(SecurityUtils.isCurrentUser(userId)).isTrue();
    }

    @Test
    @DisplayName("異なるユーザーの場合はfalseを返す")
    void should_returnFalse_when_differentUser() {
        // Arrange
        setAuthentication(userId, "USER");

        // Act & Assert
        assertThat(SecurityUtils.isCurrentUser(UUID.randomUUID())).isFalse();
    }

    // --- hasRole ---

    @Test
    @DisplayName("指定ロールを持つ場合はtrueを返す")
    void should_returnTrue_when_hasRole() {
        // Arrange
        setAuthentication(userId, "ADMIN");

        // Act & Assert
        assertThat(SecurityUtils.hasRole("ADMIN")).isTrue();
    }

    @Test
    @DisplayName("指定ロールを持たない場合はfalseを返す")
    void should_returnFalse_when_doesNotHaveRole() {
        // Arrange
        setAuthentication(userId, "USER");

        // Act & Assert
        assertThat(SecurityUtils.hasRole("ADMIN")).isFalse();
    }

    @Test
    @DisplayName("未認証の場合はfalseを返す")
    void should_returnFalse_when_notAuthenticatedForHasRole() {
        assertThat(SecurityUtils.hasRole("ADMIN")).isFalse();
    }

    // --- verifyOwnershipOrAdmin ---

    @Test
    @DisplayName("リソース所有者の場合は例外をスローしない")
    void should_notThrow_when_resourceOwner() {
        // Arrange
        setAuthentication(userId, "USER");

        // Act & Assert (no exception)
        SecurityUtils.verifyOwnershipOrAdmin(userId);
    }

    @Test
    @DisplayName("ADMINロールの場合は他ユーザーのリソースでも例外をスローしない")
    void should_notThrow_when_admin() {
        // Arrange
        setAuthentication(userId, "ADMIN");

        // Act & Assert (no exception)
        SecurityUtils.verifyOwnershipOrAdmin(UUID.randomUUID());
    }

    @Test
    @DisplayName("非所有者かつ非ADMINの場合はAuthorizationDeniedExceptionをスローする")
    void should_throw_when_notOwnerAndNotAdmin() {
        // Arrange
        setAuthentication(userId, "USER");
        UUID otherUserId = UUID.randomUUID();

        // Act & Assert
        assertThatThrownBy(() -> SecurityUtils.verifyOwnershipOrAdmin(otherUserId))
                .isInstanceOf(AuthorizationDeniedException.class);
    }

    private void setAuthentication(UUID principal, String role) {
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
        var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
