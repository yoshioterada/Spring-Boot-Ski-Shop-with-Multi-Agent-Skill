package com.example.skishop.usermanagement.repository;

import com.example.skishop.usermanagement.model.UserActivity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserActivityRepository extends JpaRepository<UserActivity, Long> {
    Page<UserActivity> findByUserId(UUID userId, Pageable pageable);
}
