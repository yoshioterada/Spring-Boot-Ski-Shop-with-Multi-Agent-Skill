package com.example.skishop.coupon.repository;

import com.example.skishop.coupon.model.UserCoupon;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserCouponRepository extends JpaRepository<UserCoupon, UUID> {

    @EntityGraph(attributePaths = {"coupon"})
    List<UserCoupon> findByUserIdAndRedeemedFalse(UUID userId);

    Optional<UserCoupon> findByUserIdAndCouponId(UUID userId, UUID couponId);
}
