package com.example.skishop.authentication.controller;

import com.example.skishop.authentication.dto.*;
import com.example.skishop.authentication.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // 認可不要: ログインエンドポイント（公開）
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        String ipAddress = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        TokenResponse response = authService.login(request, ipAddress, userAgent);
        return ResponseEntity.ok(response);
    }

    // 認可不要: 登録エンドポイント（公開）
    @PostMapping("/register")
    public ResponseEntity<UserInfoResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserInfoResponse response = authService.register(request);
        return ResponseEntity.created(URI.create("/api/v1/auth/me")).body(response);
    }

    // 認可不要: リフレッシュエンドポイント（公開、リフレッシュトークンで認証）
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        TokenResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestBody(required = false) LogoutRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String refreshToken = request != null ? request.refreshToken() : null;
        authService.logout(refreshToken);
        return ResponseEntity.noContent().build();
    }

    // 認可不要: トークン検証エンドポイント（他サービスから呼ばれる）
    @GetMapping("/validate")
    public ResponseEntity<TokenValidationResponse> validateToken(
            @RequestParam String token) {
        TokenValidationResponse response = authService.validateToken(token);
        if (response.valid()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserInfoResponse> getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getSubject();
        UserInfoResponse response = authService.getCurrentUser(username);
        return ResponseEntity.ok(response);
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }
}
