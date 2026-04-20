package com.example.skishop.usermanagement.service;

import com.example.skishop.usermanagement.model.RoleEntity;
import com.example.skishop.usermanagement.model.UserProfile;
import com.example.skishop.usermanagement.model.VerificationToken;
import com.example.skishop.usermanagement.repository.RoleEntityRepository;
import com.example.skishop.usermanagement.repository.UserProfileRepository;
import com.example.skishop.usermanagement.repository.VerificationTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class UserRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(UserRegistrationService.class);
    private static final long TOKEN_EXPIRY_HOURS = 24;

    private final UserProfileRepository userProfileRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final RoleEntityRepository roleEntityRepository;

    public UserRegistrationService(UserProfileRepository userProfileRepository,
                                   VerificationTokenRepository verificationTokenRepository,
                                   RoleEntityRepository roleEntityRepository) {
        this.userProfileRepository = userProfileRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.roleEntityRepository = roleEntityRepository;
    }

    @Transactional
    public void handleUserRegistered(UUID userId, String email, String firstName,
                                     String lastName, String verificationToken) {
        if (userProfileRepository.existsByEmail(email)) {
            log.info("User profile already exists for email: {}, saving new token", maskEmail(email));
            userProfileRepository.findByEmail(email).ifPresent(existing ->
                    saveVerificationToken(existing.getId(), verificationToken));
            return;
        }

        RoleEntity customerRole = roleEntityRepository.findByName("CUSTOMER")
                .orElseGet(() -> roleEntityRepository.save(new RoleEntity("CUSTOMER", "Default customer role")));

        UserProfile profile = new UserProfile(email, null, firstName, lastName);
        if (userId != null) {
            profile.setId(userId);
        }
        profile.setStatus(UserProfile.UserStatus.PENDING_VERIFICATION);
        profile.setRole(customerRole);
        userProfileRepository.save(profile);

        log.info("Created user profile for: {}", maskEmail(email));

        saveVerificationToken(profile.getId(), verificationToken);
    }

    private void saveVerificationToken(UUID userId, String token) {
        verificationTokenRepository.deleteByUserIdAndUsedFalse(userId);
        Instant expiresAt = Instant.now().plus(TOKEN_EXPIRY_HOURS, ChronoUnit.HOURS);
        verificationTokenRepository.save(new VerificationToken(userId, token, expiresAt));
        log.info("Saved verification token for userId: {}, expires: {}", userId, expiresAt);
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        int atIdx = email.indexOf('@');
        return email.substring(0, Math.min(2, atIdx)) + "***" + email.substring(atIdx);
    }
}
