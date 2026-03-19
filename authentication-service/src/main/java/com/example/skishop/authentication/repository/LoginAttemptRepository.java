package com.example.skishop.authentication.repository;

import com.example.skishop.authentication.entity.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, UUID> {
    List<LoginAttempt> findByUserIdAndTimestampAfterOrderByTimestampDesc(UUID userId, LocalDateTime after);
    long countByUserIdAndIsSuccessFalseAndTimestampAfter(UUID userId, LocalDateTime after);
}
