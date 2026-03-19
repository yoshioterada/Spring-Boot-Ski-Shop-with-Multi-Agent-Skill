package com.example.skishop.auth.repository;

import com.example.skishop.auth.model.SecurityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SecurityLogRepository extends JpaRepository<SecurityLog, UUID> {

    List<SecurityLog> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<SecurityLog> findByEventTypeOrderByCreatedAtDesc(String eventType);
}
