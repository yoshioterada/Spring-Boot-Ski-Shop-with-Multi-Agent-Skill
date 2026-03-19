package com.example.skishop.point.repository;

import com.example.skishop.point.model.UserTier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserTierRepository extends JpaRepository<UserTier, UUID> {

    Optional<UserTier> findByUserId(UUID userId);
}
