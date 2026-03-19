package com.example.skishop.usermanagement.repository;

import com.example.skishop.usermanagement.model.UserPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserPreferenceRepository extends JpaRepository<UserPreference, UUID> {

    List<UserPreference> findByUserId(UUID userId);

    Optional<UserPreference> findByUserIdAndPrefKey(UUID userId, String prefKey);

    void deleteByUserIdAndPrefKey(UUID userId, String prefKey);
}
