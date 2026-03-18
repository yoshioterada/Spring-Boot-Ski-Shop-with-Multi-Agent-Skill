package com.example.skishop.authentication.controller;

import com.example.skishop.authentication.TestSecurityConfig;
import com.example.skishop.authentication.config.SecurityConfig;
import com.example.skishop.authentication.dto.*;
import com.example.skishop.authentication.exception.*;
import com.example.skishop.authentication.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    value = AuthController.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class)
)
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    // --- POST /api/v1/auth/login ---

    @Test
    @DisplayName("正しい認証情報でログインすると 200 とトークンが返る")
    void should_return200WithTokens_when_loginWithValidCredentials() throws Exception {
        // Arrange
        LoginRequest request = new LoginRequest("testuser", "password123");
        TokenResponse response = TokenResponse.of("access.token", "refresh.token", 900L);
        when(authService.login(any(LoginRequest.class), any(), any())).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").value("access.token"))
            .andExpect(jsonPath("$.refreshToken").value("refresh.token"))
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    @DisplayName("バリデーションエラーのあるログインリクエストは 400 を返す")
    void should_return400_when_loginRequestIsInvalid() throws Exception {
        // Arrange - username too short (< 3 chars), password too short (< 8 chars)
        LoginRequest request = new LoginRequest("ab", "short");

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Validation Failed"));
    }

    @Test
    @DisplayName("存在しないユーザーでログインすると 401 を返す")
    void should_return401_when_loginWithInvalidCredentials() throws Exception {
        // Arrange
        LoginRequest request = new LoginRequest("unknownuser", "password123");
        when(authService.login(any(), any(), any()))
            .thenThrow(new AuthenticationException("ユーザー名またはパスワードが正しくありません"));

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("ロックされたアカウントでログインすると 423 を返す")
    void should_return423_when_accountIsLocked() throws Exception {
        // Arrange
        LoginRequest request = new LoginRequest("lockeduser", "password123");
        when(authService.login(any(), any(), any()))
            .thenThrow(new AccountLockedException(LocalDateTime.now().plusMinutes(10)));

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isLocked());
    }

    // --- POST /api/v1/auth/register ---

    @Test
    @DisplayName("有効なリクエストで登録すると 201 とユーザー情報が返る")
    void should_return201WithUserInfo_when_registerWithValidData() throws Exception {
        // Arrange
        RegisterRequest request = new RegisterRequest("newuser", "new@example.com", "password123");
        UserInfoResponse response = new UserInfoResponse(
            "uuid-123", "newuser", "new@example.com", List.of("ROLE_USER"), true);
        when(authService.register(any(RegisterRequest.class))).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.username").value("newuser"))
            .andExpect(jsonPath("$.email").value("new@example.com"));
    }

    @Test
    @DisplayName("重複ユーザー名で登録すると 409 を返す")
    void should_return409_when_usernameAlreadyExists() throws Exception {
        // Arrange
        RegisterRequest request = new RegisterRequest("existing", "new@example.com", "password123");
        when(authService.register(any()))
            .thenThrow(new UserAlreadyExistsException("ユーザー名 'existing' は既に使用されています"));

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("無効なメールアドレスで登録すると 400 を返す")
    void should_return400_when_emailIsInvalid() throws Exception {
        // Arrange
        RegisterRequest request = new RegisterRequest("newuser", "not-an-email", "password123");

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Validation Failed"));
    }

    // --- POST /api/v1/auth/refresh ---

    @Test
    @DisplayName("有効なリフレッシュトークンで新しいトークンが返る")
    void should_return200WithNewTokens_when_refreshTokenIsValid() throws Exception {
        // Arrange
        RefreshTokenRequest request = new RefreshTokenRequest("valid-refresh-token");
        TokenResponse response = TokenResponse.of("new.access.token", "new-refresh-token", 900L);
        when(authService.refreshToken(any(RefreshTokenRequest.class))).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").value("new.access.token"));
    }

    @Test
    @DisplayName("無効なリフレッシュトークンで 401 を返す")
    void should_return401_when_refreshTokenIsInvalid() throws Exception {
        // Arrange
        RefreshTokenRequest request = new RefreshTokenRequest("invalid-refresh-token");
        when(authService.refreshToken(any()))
            .thenThrow(new InvalidTokenException());

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized());
    }

    // --- GET /api/v1/auth/validate ---

    @Test
    @DisplayName("有効なトークンで validate すると 200 を返す")
    void should_return200_when_tokenIsValid() throws Exception {
        // Arrange
        TokenValidationResponse response = new TokenValidationResponse(
            true, "testuser", List.of("ROLE_USER"),
            LocalDateTime.now().plusMinutes(15));
        when(authService.validateToken("valid.token")).thenReturn(response);

        // Act & Assert
        mockMvc.perform(get("/api/v1/auth/validate")
                .param("token", "valid.token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valid").value(true))
            .andExpect(jsonPath("$.subject").value("testuser"));
    }

    @Test
    @DisplayName("無効なトークンで validate すると 401 を返す")
    void should_return401_when_tokenIsInvalidForValidate() throws Exception {
        // Arrange
        when(authService.validateToken("bad.token")).thenReturn(TokenValidationResponse.invalid());

        // Act & Assert
        mockMvc.perform(get("/api/v1/auth/validate")
                .param("token", "bad.token"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.valid").value(false));
    }

    // --- POST /api/v1/auth/logout ---

    @Test
    @DisplayName("認証済みユーザーがログアウトすると 204 を返す")
    void should_return204_when_logoutWithValidJwt() throws Exception {
        // Arrange
        LogoutRequest request = new LogoutRequest("some-refresh-token");
        doNothing().when(authService).logout(any());

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/logout")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(b -> b.subject("testuser")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNoContent());

        verify(authService).logout("some-refresh-token");
    }

    // --- GET /api/v1/auth/me ---

    @Test
    @DisplayName("認証済みユーザーが /me にアクセスするとユーザー情報が返る")
    void should_returnUserInfo_when_authenticatedUserCallsMe() throws Exception {
        // Arrange
        UserInfoResponse response = new UserInfoResponse(
            "uuid-123", "testuser", "test@example.com", List.of("ROLE_USER"), true);
        when(authService.getCurrentUser("testuser")).thenReturn(response);

        // Act & Assert
        mockMvc.perform(get("/api/v1/auth/me")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(b -> b.subject("testuser"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("testuser"))
            .andExpect(jsonPath("$.email").value("test@example.com"));
    }

    @Test
    @DisplayName("未認証で /me にアクセスすると 401 を返す")
    void should_return401_when_unauthenticatedUserCallsMe() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/v1/auth/me"))
            .andExpect(status().isUnauthorized());
    }
}
