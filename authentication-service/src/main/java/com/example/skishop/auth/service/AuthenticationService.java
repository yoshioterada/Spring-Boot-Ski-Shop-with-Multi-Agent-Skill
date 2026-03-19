package com.example.skishop.auth.service;

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
import com.example.skishop.auth.model.PasswordReset;
import com.example.skishop.auth.model.User;
import com.example.skishop.auth.model.UserSession;
import com.example.skishop.auth.repository.PasswordResetRepository;
import com.example.skishop.auth.repository.UserRepository;
import com.example.skishop.auth.repository.UserSessionRepository;
import com.example.skishop.common.event.DomainEvent;
import com.example.skishop.common.event.EventPublisher;
import com.example.skishop.common.exception.AuthenticationFailedException;
import com.example.skishop.common.exception.BusinessRuleViolationException;
import com.example.skishop.common.exception.ResourceNotFoundException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

@Service
public class AuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);
    private static final int MAX_FAILED_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;
    private final PasswordResetRepository passwordResetRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final EventPublisher eventPublisher;
    private final SecurityLogService securityLogService;

    public AuthenticationService(UserRepository userRepository,
                                  UserSessionRepository userSessionRepository,
                                  PasswordResetRepository passwordResetRepository,
                                  PasswordEncoder passwordEncoder,
                                  JwtTokenService jwtTokenService,
                                  EventPublisher eventPublisher,
                                  SecurityLogService securityLogService) {
        this.userRepository = userRepository;
        this.userSessionRepository = userSessionRepository;
        this.passwordResetRepository = passwordResetRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.eventPublisher = eventPublisher;
        this.securityLogService = securityLogService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.info("Processing registration for email: {}", maskEmail(request.email()));

        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessRuleViolationException("EMAIL_ALREADY_EXISTS",
                    "このメールアドレスは既に登録されています");
        }

        String encodedPassword = passwordEncoder.encode(request.password());
        var user = new User(request.email(), encodedPassword, request.firstName(), request.lastName());
        user.setStatus(User.UserStatus.ACTIVE);
        user.setEmailVerified(true);
        user = userRepository.save(user);

        log.info("User registered successfully: {}", user.getId());

        String verificationToken = UUID.randomUUID().toString();
        eventPublisher.publish(DomainEvent.create(
                "USER_REGISTERED",
                "authentication-service",
                new UserRegisteredPayload(user.getId(), user.getEmail(),
                        user.getFirstName(), user.getLastName(), user.getRole().name(),
                        verificationToken)
        ));

        return createAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        return login(request, null, null);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String ipAddress, String userAgent) {
        log.info("Processing login for email: {}", maskEmail(request.email()));

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> {
                    securityLogService.logLoginFailure(request.email(), ipAddress, userAgent, "User not found");
                    return new AuthenticationFailedException("メールアドレスまたはパスワードが正しくありません");
                });

        if (user.isAccountLocked()) {
            securityLogService.logLoginFailure(request.email(), ipAddress, userAgent, "Account locked");
            throw new AuthenticationFailedException("アカウントがロックされています。管理者にお問い合わせください");
        }

        if (!user.isActive()) {
            securityLogService.logLoginFailure(request.email(), ipAddress, userAgent, "Account inactive");
            throw new AuthenticationFailedException("アカウントが無効化されています");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            handleFailedLogin(user, ipAddress, userAgent);
            throw new AuthenticationFailedException("メールアドレスまたはパスワードが正しくありません");
        }

        user.setFailedLoginAttempts(0);
        user.setLastLogin(Instant.now());
        userRepository.save(user);

        securityLogService.logLoginSuccess(user.getId(), ipAddress, userAgent);

        log.info("User logged in successfully: {}", user.getId());
        return createAuthResponse(user);
    }

    @Transactional
    public AuthResponse refreshToken(TokenRefreshRequest request) {
        log.info("Processing token refresh");

        try {
            Claims claims = jwtTokenService.validateToken(request.refreshToken());
            String tokenType = claims.get("type", String.class);

            if (!"refresh".equals(tokenType)) {
                throw new AuthenticationFailedException("無効なリフレッシュトークンです");
            }

            UUID userId = UUID.fromString(claims.getSubject());
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new AuthenticationFailedException("ユーザーが見つかりません"));

            if (!user.isActive() || user.isAccountLocked()) {
                throw new AuthenticationFailedException("アカウントが無効です");
            }

            return createAuthResponse(user);
        } catch (JwtException e) {
            throw new AuthenticationFailedException("リフレッシュトークンが無効または期限切れです");
        }
    }

    @Transactional
    public MessageResponse logout(UUID userId, String ipAddress) {
        log.info("Processing logout for userId: {}", userId);

        userSessionRepository.deactivateAllByUserId(userId);
        securityLogService.logLogout(userId, ipAddress);

        eventPublisher.publish(DomainEvent.create(
                "USER_LOGGED_OUT",
                "authentication-service",
                new UserLogoutPayload(userId, Instant.now())
        ));

        return new MessageResponse("ログアウトしました");
    }

    public TokenValidationResponse validateToken(String token) {
        try {
            Claims claims = jwtTokenService.validateToken(token);
            String tokenType = claims.get("type", String.class);

            if (!"access".equals(tokenType)) {
                return new TokenValidationResponse(false, null, null, null, null);
            }

            return new TokenValidationResponse(
                    true,
                    claims.getSubject(),
                    claims.get("email", String.class),
                    claims.get("role", String.class),
                    claims.getExpiration().toInstant()
            );
        } catch (JwtException e) {
            return new TokenValidationResponse(false, null, null, null, null);
        }
    }

    @Transactional(readOnly = true)
    public UserInfoResponse getCurrentUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));

        return new UserInfoResponse(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole().name(),
                user.isEmailVerified(),
                user.getLastLogin()
        );
    }

    @Transactional
    public MessageResponse requestPasswordReset(PasswordResetRequest request) {
        log.info("Processing password reset request for email: {}", maskEmail(request.email()));

        userRepository.findByEmail(request.email()).ifPresent(user -> {
            String token = UUID.randomUUID().toString();
            var resetEntity = new PasswordReset(user.getId(), token,
                    Instant.now().plus(1, ChronoUnit.HOURS));
            passwordResetRepository.save(resetEntity);
            log.info("Password reset token generated for userId: {}", user.getId());

            eventPublisher.publish(DomainEvent.create(
                    "PASSWORD_RESET_REQUESTED",
                    "authentication-service",
                    new PasswordResetRequestedPayload(user.getId(), user.getEmail(), user.getFirstName(),
                            token, resetEntity.getExpiresAt())
            ));
        });

        // 常に同じレスポンスを返すことでメールアドレスの存在を漏洩しない
        return new MessageResponse("パスワードリセットのメールを送信しました");
    }

    @Transactional
    public MessageResponse confirmPasswordReset(PasswordResetConfirmRequest request) {
        log.info("Processing password reset confirmation");

        PasswordReset resetEntry = passwordResetRepository.findByTokenAndUsedFalse(request.token())
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "INVALID_RESET_TOKEN", "無効または期限切れのリセットトークンです"));

        if (resetEntry.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessRuleViolationException("EXPIRED_RESET_TOKEN", "リセットトークンが期限切れです");
        }

        User user = userRepository.findById(resetEntry.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", resetEntry.getUserId().toString()));

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        resetEntry.setUsed(true);
        passwordResetRepository.save(resetEntry);

        securityLogService.logPasswordChanged(user.getId(), null);

        eventPublisher.publish(DomainEvent.create(
                "PASSWORD_CHANGED",
                "authentication-service",
                new PasswordChangedPayload(user.getId(), Instant.now())
        ));

        return new MessageResponse("パスワードがリセットされました");
    }

    @Transactional
    public MessageResponse changePassword(UUID userId, PasswordChangeRequest request) {
        log.info("Processing password change for userId: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new AuthenticationFailedException("現在のパスワードが正しくありません");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        securityLogService.logPasswordChanged(userId, null);

        eventPublisher.publish(DomainEvent.create(
                "PASSWORD_CHANGED",
                "authentication-service",
                new PasswordChangedPayload(userId, Instant.now())
        ));

        return new MessageResponse("パスワードが変更されました");
    }

    @Transactional
    public MessageResponse softDeleteUser(UUID userId, String reason) {
        log.info("Processing soft delete for userId: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));

        user.setActive(false);
        user.setStatus(User.UserStatus.DEACTIVATED);
        userRepository.save(user);

        userSessionRepository.deactivateAllByUserId(userId);

        eventPublisher.publish(DomainEvent.create(
                "USER_DELETED",
                "authentication-service",
                new UserDeletedPayload(userId, reason, false)
        ));

        return new MessageResponse("ユーザーアカウントが無効化されました");
    }

    @Transactional
    public MessageResponse hardDeleteUser(UUID userId, String reason) {
        log.info("Processing hard delete for userId: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));

        userSessionRepository.deactivateAllByUserId(userId);
        userRepository.delete(user);

        eventPublisher.publish(DomainEvent.create(
                "USER_DELETED",
                "authentication-service",
                new UserDeletedPayload(userId, reason, true)
        ));

        return new MessageResponse("ユーザーアカウントが完全に削除されました");
    }

    @Transactional(readOnly = true)
    public User findById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));
    }

    private void handleFailedLogin(User user, String ipAddress, String userAgent) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);

        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setAccountLocked(true);
            user.setLockedAt(Instant.now());
            log.warn("Account locked due to {} failed attempts: {}", attempts, user.getId());
            securityLogService.logAccountLocked(user.getId(), ipAddress);

            eventPublisher.publish(DomainEvent.create(
                    "ACCOUNT_LOCKED",
                    "authentication-service",
                    new AccountLockedPayload(user.getId(), attempts)
            ));
        }

        securityLogService.logLoginFailure(user.getEmail(), ipAddress, userAgent, "Invalid password");
        userRepository.save(user);
    }

    private AuthResponse createAuthResponse(User user) {
        String accessToken = jwtTokenService.generateAccessToken(user);
        String refreshToken = jwtTokenService.generateRefreshToken(user);

        return new AuthResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole().name(),
                accessToken,
                refreshToken,
                Instant.now().plus(1, ChronoUnit.HOURS)
        );
    }

    public record UserRegisteredPayload(UUID userId, String email, String firstName,
                                         String lastName, String role, String verificationToken) {}

    public record PasswordResetRequestedPayload(
            UUID userId, String email, String firstName,
            String resetToken, Instant expiresAt) {}

    public record UserLogoutPayload(UUID userId, Instant logoutAt) {}
    public record PasswordChangedPayload(UUID userId, Instant changedAt) {}
    public record AccountLockedPayload(UUID userId, int failedAttempts) {}
    public record UserDeletedPayload(UUID userId, String reason, boolean isHardDelete) {}

    private static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        String[] parts = email.split("@");
        String local = parts[0];
        String masked = local.length() <= 2
                ? "*" + "@" + parts[1]
                : local.substring(0, 2) + "***" + "@" + parts[1];
        return masked;
    }
}
