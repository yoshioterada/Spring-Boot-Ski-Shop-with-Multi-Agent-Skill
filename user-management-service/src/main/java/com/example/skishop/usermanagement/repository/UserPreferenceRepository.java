package com.example.skishop.usermanagement.repository;

import com.example.skishop.usermanagement.model.UserPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserPreferenceRepository extends JpaRepository<UserPreference, Long> {
    Optional<UserPreference> findByUserId(UUID userId);
}
