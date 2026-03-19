package com.example.skishop.authentication.repository;

import com.example.skishop.authentication.entity.OAuthToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OAuthTokenRepository extends JpaRepository<OAuthToken, UUID> {
    Optional<OAuthToken> findByAccessToken(String accessToken);
    Optional<OAuthToken> findByRefreshToken(String refreshToken);
    List<OAuthToken> findByUserId(UUID userId);
    void deleteByExpiresAtBeforeAndRevokedFalse(LocalDateTime dateTime);
}
