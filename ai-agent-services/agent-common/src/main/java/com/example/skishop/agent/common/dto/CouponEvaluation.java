package com.example.skishop.agent.common.dto;

import java.math.BigDecimal;
import java.util.List;

public record CouponEvaluation(
        List<CouponCandidate> appliedCoupons,
        BigDecimal totalDiscountAmount,
        BigDecimal finalCartTotal,
        double optimizationScore,
        boolean meetsConstraints,
        String evaluationReason
) {}
