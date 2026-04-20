package com.example.skishop.usermanagement.repository;

import com.example.skishop.usermanagement.model.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {

    Optional<VerificationToken> findByTokenAndUsedFalse(String token);

    @Transactional
    void deleteByUserIdAndUsedFalse(UUID userId);
}
