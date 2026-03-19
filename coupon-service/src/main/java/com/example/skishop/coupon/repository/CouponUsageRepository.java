package com.example.skishop.coupon.repository;

import com.example.skishop.coupon.model.CouponUsage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CouponUsageRepository extends JpaRepository<CouponUsage, UUID> {

    Page<CouponUsage> findByCouponId(UUID couponId, Pageable pageable);

    long countByCouponIdAndUserId(UUID couponId, UUID userId);
}
