package com.example.skishop.authentication.service;

import com.example.skishop.authentication.config.AppSecurityProperties;
import com.example.skishop.authentication.config.JwtConfig;
import com.example.skishop.authentication.dto.*;
import com.example.skishop.authentication.entity.LoginAttempt;
import com.example.skishop.authentication.entity.User;
import com.example.skishop.authentication.exception.*;
import com.example.skishop.authentication.repository.LoginAttemptRepository;
import com.example.skishop.authentication.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final TokenStorageService tokenStorageService;
    private final AppSecurityProperties securityProperties;
    private final JwtConfig jwtConfig;

    public AuthService(
            UserRepository userRepository,
            LoginAttemptRepository loginAttemptRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService,
            TokenStorageService tokenStorageService,
            AppSecurityProperties securityProperties,
            JwtConfig jwtConfig) {
        this.userRepository = userRepository;
        this.loginAttemptRepository = loginAttemptRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.tokenStorageService = tokenStorageService;
        this.securityProperties = securityProperties;
        this.jwtConfig = jwtConfig;
    }

    public TokenResponse login(LoginRequest request, String ipAddress, String userAgent) {
        User user = userRepository.findByUsername(request.username())
            .orElseThrow(() -> {
                recordLoginAttempt(null, ipAddress, userAgent, false, "ユーザーが見つかりません");
                return new AuthenticationException("ユーザー名またはパスワードが正しくありません");
            });

        checkAccountLock(user);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            handleFailedLogin(user, ipAddress, userAgent);
            throw new AuthenticationException("ユーザー名またはパスワードが正しくありません");
        }

        if (!user.isEnabled()) {
            throw new AuthenticationException("アカウントが無効です");
        }

        resetFailedAttempts(user);
        recordLoginAttempt(user.getId().toString(), ipAddress, userAgent, true, null);

        String accessToken = jwtTokenService.generateAccessToken(user);
        String refreshToken = tokenStorageService.generateRefreshToken();
        tokenStorageService.storeRefreshToken(
            refreshToken, user.getId().toString(),
            Duration.ofSeconds(jwtConfig.getRefreshTokenExpiry())
        );

        log.info("ログイン成功: userId={}", user.getId());
        return TokenResponse.of(accessToken, refreshToken, jwtTokenService.getAccessTokenExpirySeconds());
    }

    public UserInfoResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new UserAlreadyExistsException("ユーザー名 '" + request.username() + "' は既に使用されています");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new UserAlreadyExistsException("メールアドレス '" + request.email() + "' は既に使用されています");
        }

        User user = new User(
            request.username(),
            request.email(),
            passwordEncoder.encode(request.password())
        );
        user.setRoles(Set.of("ROLE_USER"));
        User saved = userRepository.save(user);
        log.info("ユーザー登録完了: userId={}", saved.getId());
        return UserInfoResponse.from(saved);
    }

    public TokenResponse refreshToken(RefreshTokenRequest request) {
        String userId = tokenStorageService.getUserIdByRefreshToken(request.refreshToken())
            .orElseThrow(InvalidTokenException::new);

        UUID userUuid;
        try {
            userUuid = UUID.fromString(userId);
        } catch (IllegalArgumentException ex) {
            throw new InvalidTokenException();
        }

        User user = userRepository.findById(userUuid)
            .orElseThrow(InvalidTokenException::new);

        checkAccountLock(user);

        if (!user.isEnabled()) {
            throw new AuthenticationException("アカウントが無効です");
        }

        tokenStorageService.deleteRefreshToken(request.refreshToken());

        String newAccessToken = jwtTokenService.generateAccessToken(user);
        String newRefreshToken = tokenStorageService.generateRefreshToken();
        tokenStorageService.storeRefreshToken(
            newRefreshToken, userId,
            Duration.ofSeconds(jwtConfig.getRefreshTokenExpiry())
        );

        log.info("トークンリフレッシュ: userId={}", userId);
        return TokenResponse.of(newAccessToken, newRefreshToken, jwtTokenService.getAccessTokenExpirySeconds());
    }

    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            tokenStorageService.deleteRefreshToken(refreshToken);
            log.info("ログアウト: リフレッシュトークンを削除しました");
        }
    }

    @Transactional(readOnly = true)
    public TokenValidationResponse validateToken(String token) {
        if (!jwtTokenService.isTokenValid(token)) {
            return TokenValidationResponse.invalid();
        }

        try {
            var jwt = jwtTokenService.validateAndDecodeToken(token);
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles == null) roles = List.of();

            return new TokenValidationResponse(
                true,
                jwt.getSubject(),
                roles,
                jwt.getExpiresAt() != null
                    ? LocalDateTime.ofInstant(jwt.getExpiresAt(), java.time.ZoneId.systemDefault())
                    : null
            );
        } catch (Exception ex) {
            return TokenValidationResponse.invalid();
        }
    }

    @Transactional(readOnly = true)
    public UserInfoResponse getCurrentUser(String username) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new AuthenticationException("ユーザーが見つかりません"));
        return UserInfoResponse.from(user);
    }

    private void checkAccountLock(User user) {
        if (user.isLocked()) {
            LocalDateTime lockTime = user.getLockTime();
            if (lockTime != null) {
                LocalDateTime unlockAt = lockTime.plus(securityProperties.getAccountLockDuration());
                if (LocalDateTime.now().isBefore(unlockAt)) {
                    throw new AccountLockedException(unlockAt);
                } else {
                    user.setLocked(false);
                    user.setFailedAttempts(0);
                    user.setLockTime(null);
                    userRepository.save(user);
                }
            }
        }
    }

    private void handleFailedLogin(User user, String ipAddress, String userAgent) {
        int newFailedAttempts = user.getFailedAttempts() + 1;
        user.setFailedAttempts(newFailedAttempts);
        recordLoginAttempt(user.getId().toString(), ipAddress, userAgent, false, "パスワードが正しくありません");

        if (newFailedAttempts >= securityProperties.getMaxLoginAttempts()) {
            user.setLocked(true);
            user.setLockTime(LocalDateTime.now());
            log.warn("アカウントをロックしました: userId={}, failedAttempts={}", user.getId(), newFailedAttempts);
        }
        userRepository.save(user);
    }

    private void resetFailedAttempts(User user) {
        if (user.getFailedAttempts() > 0) {
            user.setFailedAttempts(0);
            userRepository.save(user);
        }
    }

    private void recordLoginAttempt(String userId, String ipAddress, String userAgent,
                                    boolean success, String failureReason) {
        UUID userUuid = userId != null ? UUID.fromString(userId) : null;
        LoginAttempt attempt = new LoginAttempt(
            userUuid, LocalDateTime.now(), ipAddress, userAgent, success, failureReason
        );
        loginAttemptRepository.save(attempt);
    }
}
