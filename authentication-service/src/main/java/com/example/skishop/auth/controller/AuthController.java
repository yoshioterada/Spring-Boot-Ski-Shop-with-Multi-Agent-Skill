package com.example.skishop.auth.controller;

import com.example.skishop.auth.dto.AuthResponse;
import com.example.skishop.auth.dto.LoginRequest;
import com.example.skishop.auth.dto.MessageResponse;
import com.example.skishop.auth.dto.PasswordChangeRequest;
import com.example.skishop.auth.dto.PasswordResetConfirmRequest;
import com.example.skishop.auth.dto.PasswordResetRequest;
import com.example.skishop.auth.dto.RegisterRequest;
import com.example.skishop.auth.dto.TokenRefreshRequest;
import com.example.skishop.auth.dto.TokenValidationResponse;
import com.example.skishop.auth.dto.UserInfoResponse;
import com.example.skishop.auth.service.AuthenticationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationService authenticationService;

    public AuthController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Registration request received");
        AuthResponse response = authenticationService.register(request);
        return ResponseEntity
                .created(URI.create("/api/v1/auth/users/" + response.userId()))
                .body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest httpRequest) {
        log.info("Login request received");
        String ipAddress = extractIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        AuthResponse response = authenticationService.login(request, ipAddress, userAgent);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody TokenRefreshRequest request) {
        log.info("Token refresh request received");
        AuthResponse response = authenticationService.refreshToken(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(@AuthenticationPrincipal UUID userId,
                                                    HttpServletRequest httpRequest) {
        log.info("Logout request received");
        String ipAddress = extractIpAddress(httpRequest);
        MessageResponse response = authenticationService.logout(userId, ipAddress);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/validate")
    public ResponseEntity<TokenValidationResponse> validate(HttpServletRequest httpRequest) {
        log.info("Token validation request received");
        String token = extractBearerToken(httpRequest);
        TokenValidationResponse response = authenticationService.validateToken(token);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<UserInfoResponse> getCurrentUser(@AuthenticationPrincipal UUID userId) {
        log.info("Current user info request received");
        UserInfoResponse response = authenticationService.getCurrentUser(userId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/password/reset")
    public ResponseEntity<MessageResponse> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request) {
        log.info("Password reset request received");
        authenticationService.requestPasswordReset(request);
        return ResponseEntity.ok(new MessageResponse("パスワードリセットのメールを送信しました"));
    }

    @PostMapping("/password/confirm")
    public ResponseEntity<MessageResponse> confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmRequest request) {
        log.info("Password reset confirmation request received");
        authenticationService.confirmPasswordReset(request);
        return ResponseEntity.ok(new MessageResponse("パスワードがリセットされました"));
    }

    @PutMapping("/password/change")
    public ResponseEntity<MessageResponse> changePassword(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody PasswordChangeRequest request) {
        log.info("Password change request received");
        authenticationService.changePassword(userId, request);
        return ResponseEntity.ok(new MessageResponse("パスワードが変更されました"));
    }

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<MessageResponse> softDeleteUser(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "ユーザーによる削除要求") String reason) {
        log.info("Soft delete request received for userId: {}", userId);
        authenticationService.softDeleteUser(userId, reason);
        return ResponseEntity.ok(new MessageResponse("ユーザーが無効化されました"));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/users/{userId}/hard")
    public ResponseEntity<Void> hardDeleteUser(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "管理者による完全削除") String reason) {
        log.info("Hard delete request received for userId: {}", userId);
        authenticationService.hardDeleteUser(userId, reason);
        return ResponseEntity.noContent().build();
    }

    private String extractIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String extractBearerToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        throw new IllegalArgumentException("Authorization ヘッダーに Bearer トークンが必要です");
    }
}
