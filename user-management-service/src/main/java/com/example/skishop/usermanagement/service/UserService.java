package com.example.skishop.usermanagement.service;

import com.example.skishop.common.event.DomainEvent;
import com.example.skishop.common.event.EventPublisher;
import com.example.skishop.common.exception.BusinessRuleViolationException;
import com.example.skishop.common.exception.ResourceNotFoundException;
import com.example.skishop.usermanagement.dto.*;
import com.example.skishop.usermanagement.model.RoleEntity;
import com.example.skishop.usermanagement.model.UserActivity;
import com.example.skishop.usermanagement.model.UserPreference;
import com.example.skishop.usermanagement.model.UserProfile;
import com.example.skishop.usermanagement.model.UserProfile.Gender;
import com.example.skishop.usermanagement.repository.RoleEntityRepository;
import com.example.skishop.usermanagement.repository.UserActivityRepository;
import com.example.skishop.usermanagement.repository.UserPreferenceRepository;
import com.example.skishop.usermanagement.repository.UserProfileRepository;
import com.example.skishop.usermanagement.repository.VerificationTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserProfileRepository userProfileRepository;
    private final RoleEntityRepository roleEntityRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final UserActivityRepository userActivityRepository;
    private final PasswordEncoder passwordEncoder;
    private final EventPublisher eventPublisher;
    private final VerificationTokenRepository verificationTokenRepository;

    public UserService(UserProfileRepository userProfileRepository,
                       RoleEntityRepository roleEntityRepository,
                       UserPreferenceRepository userPreferenceRepository,
                       UserActivityRepository userActivityRepository,
                       PasswordEncoder passwordEncoder,
                       EventPublisher eventPublisher,
                       VerificationTokenRepository verificationTokenRepository) {
        this.userProfileRepository = userProfileRepository;
        this.roleEntityRepository = roleEntityRepository;
        this.userPreferenceRepository = userPreferenceRepository;
        this.userActivityRepository = userActivityRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
        this.verificationTokenRepository = verificationTokenRepository;
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        log.info("Creating user with email: {}", maskEmail(request.email()));

        if (userProfileRepository.existsByEmail(request.email())) {
            throw new BusinessRuleViolationException("EMAIL_ALREADY_EXISTS",
                    "このメールアドレスは既に登録されています");
        }

        String encodedPassword = passwordEncoder.encode(request.password());
        var user = new UserProfile(request.email(), encodedPassword, request.firstName(), request.lastName());
        user.setPhoneNumber(request.phoneNumber());
        user.setBirthDate(request.birthDate());
        if (request.gender() != null) {
            user.setGender(Gender.valueOf(request.gender().toUpperCase()));
        }
        user.setStatus(UserProfile.UserStatus.PENDING_VERIFICATION);

        RoleEntity customerRole = roleEntityRepository.findByName("CUSTOMER")
                .orElseGet(() -> roleEntityRepository.save(new RoleEntity("CUSTOMER", "Default customer role")));
        user.setRole(customerRole);

        user = userProfileRepository.save(user);
        log.info("User created successfully: {}", user.getId());

        eventPublisher.publish(DomainEvent.create("user.created", "user-management-service",
                new UserEventPayload(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName())));

        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID userId) {
        UserProfile user = findUserOrThrow(userId);
        return toResponse(user);
    }

    /**
     * Multi-Agent Orchestrator 向けの拡張プロファイル取得。
     * customerTier / pointBalance は他サービス連携が未統合のため暫定スタブ値を返す。
     */
    @Transactional(readOnly = true)
    public com.example.skishop.usermanagement.dto.UserProfileResponse getUserProfile(UUID userId) {
        UserProfile user = findUserOrThrow(userId);
        String displayName = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim();
        if (displayName.isEmpty()) {
            displayName = user.getEmail();
        }
        String skillLevel = userPreferenceRepository
                .findByUserIdAndPrefKey(userId, "preferred_skill_level")
                .map(p -> p.getPrefValue())
                .orElse("INTERMEDIATE");
        return new com.example.skishop.usermanagement.dto.UserProfileResponse(
                userId,
                displayName,
                "STANDARD",
                java.util.List.of(),
                skillLevel,
                0L);
    }

    @Transactional
    public UserResponse updateUser(UUID userId, UpdateUserRequest request) {
        log.info("Updating user: {}", userId);
        UserProfile user = findUserOrThrow(userId);

        if (request.firstName() != null) user.setFirstName(request.firstName());
        if (request.lastName() != null) user.setLastName(request.lastName());
        if (request.phoneNumber() != null) user.setPhoneNumber(request.phoneNumber());
        if (request.address() != null) user.setAddress(request.address());
        if (request.birthDate() != null) user.setBirthDate(request.birthDate());
        if (request.gender() != null) user.setGender(Gender.valueOf(request.gender().toUpperCase()));

        user = userProfileRepository.save(user);

        eventPublisher.publish(DomainEvent.create("user.updated", "user-management-service",
                new UserEventPayload(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName())));

        return toResponse(user);
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        log.info("Changing password for user: {}", userId);
        UserProfile user = findUserOrThrow(userId);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessRuleViolationException("INVALID_PASSWORD", "現在のパスワードが正しくありません");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userProfileRepository.save(user);

        eventPublisher.publish(DomainEvent.create("user.password_changed", "user-management-service",
                new UserEventPayload(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName())));
    }

    @Transactional
    public void updateUserStatus(UUID userId, String status) {
        log.info("Updating status for user: {} to {}", userId, status);
        UserProfile user = findUserOrThrow(userId);
        user.setStatus(UserProfile.UserStatus.valueOf(status.toUpperCase()));
        userProfileRepository.save(user);

        try {
            eventPublisher.publish(DomainEvent.create("user.updated", "user-management-service",
                    new UserEventPayload(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName())));
        } catch (Exception e) {
            log.warn("Failed to publish user.updated event for user {}: {}", userId, e.getMessage());
        }
    }

    @Transactional
    public UserResponse deleteUser(UUID userId) {
        log.info("Soft-deleting user: {}", userId);
        UserProfile user = findUserOrThrow(userId);

        if (user.getStatus() == UserProfile.UserStatus.DEACTIVATED) {
            throw new BusinessRuleViolationException("USER_ALREADY_DEACTIVATED", "このユーザーは既に無効化されています");
        }

        user.setStatus(UserProfile.UserStatus.DEACTIVATED);
        user = userProfileRepository.save(user);

        eventPublisher.publish(DomainEvent.create("user.deleted", "user-management-service",
                new UserEventPayload(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName())));

        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public boolean checkEmailExists(String email) {
        return userProfileRepository.existsByEmail(email);
    }

    @Transactional
    public void verifyEmail(String token) {
        log.info("Verifying email with token");

        var vt = verificationTokenRepository.findByTokenAndUsedFalse(token)
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "INVALID_TOKEN", "無効または期限切れのトークンです"));

        if (vt.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessRuleViolationException(
                    "EXPIRED_TOKEN", "トークンが期限切れです");
        }

        UserProfile user = findUserOrThrow(vt.getUserId());
        user.setEmailVerified(true);
        if (user.getStatus() == UserProfile.UserStatus.PENDING_VERIFICATION) {
            user.setStatus(UserProfile.UserStatus.ACTIVE);
        }
        userProfileRepository.save(user);

        vt.setUsed(true);
        verificationTokenRepository.save(vt);

        eventPublisher.publish(DomainEvent.create("user.verified", "user-management-service",
                new UserEventPayload(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName())));
    }

    @Transactional
    public UserResponse assignRole(UUID userId, String roleName) {
        log.info("Assigning role {} to user {}", roleName, userId);
        UserProfile user = findUserOrThrow(userId);
        RoleEntity role = roleEntityRepository.findByName(roleName)
                .orElseThrow(() -> new ResourceNotFoundException("Role", roleName));

        user.setRole(role);
        user = userProfileRepository.save(user);

        try {
            eventPublisher.publish(DomainEvent.create("user.role_changed", "user-management-service",
                    new UserEventPayload(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName())));
        } catch (Exception e) {
            log.warn("Failed to publish role_changed event for user {}: {}", userId, e.getMessage());
        }

        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> listUsers(Pageable pageable) {
        return userProfileRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> searchUsers(String keyword, Pageable pageable) {
        if (keyword == null || keyword.isBlank()) {
            return listUsers(pageable);
        }
        return userProfileRepository.searchByKeyword(keyword.trim(), pageable).map(this::toResponse);
    }

    // --- Preferences ---

    @Transactional(readOnly = true)
    public List<PreferenceResponse> getPreferences(UUID userId) {
        findUserOrThrow(userId);
        return userPreferenceRepository.findByUserId(userId).stream()
                .map(p -> new PreferenceResponse(p.getId(), p.getPrefKey(), p.getPrefValue(), p.getPrefType(), p.getUpdatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public PreferenceResponse getPreference(UUID userId, String key) {
        findUserOrThrow(userId);
        UserPreference pref = userPreferenceRepository.findByUserIdAndPrefKey(userId, key)
                .orElseThrow(() -> new ResourceNotFoundException("Preference", key));
        return new PreferenceResponse(pref.getId(), pref.getPrefKey(), pref.getPrefValue(), pref.getPrefType(), pref.getUpdatedAt());
    }

    @Transactional
    public PreferenceResponse updatePreference(UUID userId, String key, UpdatePreferenceRequest request) {
        findUserOrThrow(userId);
        UserPreference pref = userPreferenceRepository.findByUserIdAndPrefKey(userId, key)
                .orElseGet(() -> new UserPreference(userId, key, request.value(), request.type()));

        pref.setPrefValue(request.value());
        if (request.type() != null) pref.setPrefType(request.type());
        pref = userPreferenceRepository.save(pref);

        return new PreferenceResponse(pref.getId(), pref.getPrefKey(), pref.getPrefValue(), pref.getPrefType(), pref.getUpdatedAt());
    }

    @Transactional
    public void deletePreference(UUID userId, String key) {
        findUserOrThrow(userId);
        userPreferenceRepository.deleteByUserIdAndPrefKey(userId, key);
    }

    // --- Activities ---

    @Transactional(readOnly = true)
    public Page<ActivityResponse> getActivities(UUID userId, Pageable pageable) {
        findUserOrThrow(userId);
        return userActivityRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(a -> new ActivityResponse(a.getId(), a.getUserId(), a.getActivityType(), a.getDescription(), a.getCreatedAt()));
    }

    @Transactional
    public void recordActivity(UUID userId, String activityType, String description, String ipAddress, String userAgent) {
        var activity = new UserActivity(userId, activityType, description, ipAddress, userAgent);
        userActivityRepository.save(activity);
    }

    // --- Helpers ---

    private UserProfile findUserOrThrow(UUID userId) {
        return userProfileRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));
    }

    private UserResponse toResponse(UserProfile user) {
        return new UserResponse(
                user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                user.getPhoneNumber(), user.getAddress(), user.getBirthDate(),
                user.getGender() != null ? user.getGender().name() : null,
                user.getStatus().name(), user.isEmailVerified(), user.isPhoneVerified(),
                user.getRole() != null ? user.getRole().getName() : null,
                user.getCreatedAt(), user.getUpdatedAt()
        );
    }

    public record UserEventPayload(UUID userId, String email, String firstName, String lastName) {}

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
