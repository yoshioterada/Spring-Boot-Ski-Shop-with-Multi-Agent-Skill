package com.example.skishop.point.repository;

import com.example.skishop.point.model.PointTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PointTransactionRepository extends JpaRepository<PointTransaction, UUID> {

    Page<PointTransaction> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<PointTransaction> findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            UUID userId, Instant from, Instant to, Pageable pageable);

    @Query("SELECT t FROM PointTransaction t WHERE t.userId = :userId AND t.expired = false AND t.expiresAt IS NOT NULL AND t.expiresAt <= :before AND t.type = 'EARNED'")
    List<PointTransaction> findExpiringPoints(@Param("userId") UUID userId, @Param("before") Instant before);

    @Query("SELECT COALESCE(SUM(t.points), 0) FROM PointTransaction t WHERE t.userId = :userId AND t.expired = false AND t.expiresAt IS NOT NULL AND t.expiresAt <= :before AND t.type = 'EARNED'")
    int sumExpiringPoints(@Param("userId") UUID userId, @Param("before") Instant before);

    @Query("SELECT t FROM PointTransaction t WHERE t.expired = false AND t.expiresAt IS NOT NULL AND t.expiresAt <= :now AND t.type = 'EARNED'")
    Page<PointTransaction> findAllExpiredTransactions(@Param("now") Instant now, Pageable pageable);
}
