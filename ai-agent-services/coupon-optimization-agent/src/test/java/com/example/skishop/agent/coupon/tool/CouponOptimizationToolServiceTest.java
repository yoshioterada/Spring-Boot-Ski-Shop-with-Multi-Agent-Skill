package com.example.skishop.agent.coupon.tool;

import com.example.skishop.agent.common.dto.CouponCandidate;
import com.example.skishop.agent.common.dto.CouponEvaluation;
import com.example.skishop.agent.coupon.client.CouponServiceClient;
import com.example.skishop.agent.coupon.client.PointServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CouponOptimizationToolServiceTest {

    private CouponServiceClient couponClient;
    private PointServiceClient pointClient;
    private CouponOptimizationToolService tool;

    @BeforeEach
    void setUp() {
        couponClient = mock(CouponServiceClient.class);
        pointClient = mock(PointServiceClient.class);
        tool = new CouponOptimizationToolService(couponClient, pointClient);
    }

    private CouponCandidate percent(String code, double rate, BigDecimal min, String cat,
                                     boolean stackable, int limit, LocalDate exp) {
        return new CouponCandidate("id-" + code, code, "PERCENTAGE",
                BigDecimal.valueOf(rate), BigDecimal.ZERO, min, cat, exp, stackable, limit);
    }

    private CouponCandidate fixed(String code, BigDecimal amount, BigDecimal min,
                                    boolean stackable, int limit) {
        return new CouponCandidate("id-" + code, code, "FIXED_AMOUNT",
                BigDecimal.ZERO, amount, min, null,
                LocalDate.now().plusDays(30), stackable, limit);
    }

    @Test
    void getEligibleCoupons_filters_expired_and_minimum_and_category_and_limit() {
        var ok = percent("OK10", 0.10, BigDecimal.valueOf(1000), null, true, 1, LocalDate.now().plusDays(1));
        var expired = percent("EXP", 0.10, BigDecimal.ZERO, null, true, 1, LocalDate.now().minusDays(1));
        var tooLowMin = percent("MIN", 0.10, BigDecimal.valueOf(99999), null, true, 1, LocalDate.now().plusDays(1));
        var wrongCat = percent("CAT", 0.10, BigDecimal.ZERO, "BOOTS", true, 1, LocalDate.now().plusDays(1));
        var noLimit = percent("LIM", 0.10, BigDecimal.ZERO, null, true, 0, LocalDate.now().plusDays(1));
        when(couponClient.getUserCoupons("u1"))
                .thenReturn(List.of(ok, expired, tooLowMin, wrongCat, noLimit));

        var result = tool.getEligibleCoupons("u1", BigDecimal.valueOf(5000), "SKI,WEAR", null);
        assertThat(result).hasSize(1).extracting(CouponCandidate::couponCode).containsExactly("OK10");
    }

    @Test
    void getEligibleCoupons_includes_input_coupon_code_when_found() {
        var input = percent("INPUT", 0.20, BigDecimal.ZERO, null, true, 1, LocalDate.now().plusDays(1));
        when(couponClient.getUserCoupons("u1")).thenReturn(List.of());
        when(couponClient.findByCouponCode("INPUT")).thenReturn(Optional.of(input));

        var result = tool.getEligibleCoupons("u1", BigDecimal.valueOf(1000), "", "INPUT");
        assertThat(result).hasSize(1);
    }

    @Test
    void getEligibleCoupons_handles_null_cartTotal_and_blank_category() {
        when(couponClient.getUserCoupons("u1")).thenReturn(List.of());
        var result = tool.getEligibleCoupons("u1", null, null, "");
        assertThat(result).isEmpty();
    }

    @Test
    void getEligibleCoupons_handles_null_expiresAt() {
        var c = new CouponCandidate("id", "C", "PERCENTAGE", BigDecimal.valueOf(0.10),
                BigDecimal.ZERO, null, null, null, true, 1);
        when(couponClient.getUserCoupons("u1")).thenReturn(List.of(c));
        var result = tool.getEligibleCoupons("u1", BigDecimal.valueOf(1000), "", null);
        assertThat(result).hasSize(1);
    }

    @Test
    void evaluateCouponCombinations_single_and_pair() {
        var a = percent("A", 0.10, BigDecimal.ZERO, null, true, 1, LocalDate.now().plusDays(1));
        var b = percent("B", 0.05, BigDecimal.ZERO, null, true, 1, LocalDate.now().plusDays(1));
        var c = percent("C", 0.20, BigDecimal.ZERO, null, false, 1, LocalDate.now().plusDays(1));

        var evals = tool.evaluateCouponCombinations(List.of(a, b, c), BigDecimal.valueOf(10000));
        // 3 単品 + stackable a+b の 1 ペア = 4
        assertThat(evals).hasSize(4);
        assertThat(evals.stream().anyMatch(e -> e.appliedCoupons().size() == 2)).isTrue();
    }

    @Test
    void evaluateCouponCombinations_returns_empty_for_null_or_empty_or_zero() {
        assertThat(tool.evaluateCouponCombinations(null, BigDecimal.TEN)).isEmpty();
        assertThat(tool.evaluateCouponCombinations(List.of(), BigDecimal.TEN)).isEmpty();
        assertThat(tool.evaluateCouponCombinations(
                List.of(fixed("X", BigDecimal.valueOf(100), BigDecimal.ZERO, true, 1)),
                BigDecimal.ZERO)).isEmpty();
    }

    @Test
    void selectOptimalCombination_picks_highest_score() {
        var low = new CouponEvaluation(List.of(), BigDecimal.valueOf(100), BigDecimal.valueOf(900),
                10.0, true, "low");
        var high = new CouponEvaluation(List.of(), BigDecimal.valueOf(500), BigDecimal.valueOf(500),
                50.0, true, "high");
        var unmet = new CouponEvaluation(List.of(), BigDecimal.valueOf(900), BigDecimal.valueOf(100),
                90.0, false, "unmet");
        var result = tool.selectOptimalCombination(List.of(low, unmet, high));
        assertThat(result.optimizationScore()).isEqualTo(50.0);
    }

    @Test
    void selectOptimalCombination_returns_empty_evaluation_when_input_empty_or_null() {
        var r1 = tool.selectOptimalCombination(List.of());
        var r2 = tool.selectOptimalCombination(null);
        assertThat(r1.meetsConstraints()).isFalse();
        assertThat(r2.meetsConstraints()).isFalse();
    }

    @Test
    void selectOptimalCombination_returns_empty_when_no_meet_constraints() {
        var unmet = new CouponEvaluation(List.of(), BigDecimal.valueOf(900), BigDecimal.valueOf(100),
                90.0, false, "unmet");
        var result = tool.selectOptimalCombination(List.of(unmet));
        assertThat(result.meetsConstraints()).isFalse();
    }

    @Test
    void calculatePointsUsage_normal() {
        when(pointClient.getPointBalance("u1")).thenReturn(3000);
        var r = tool.calculatePointsUsage("u1", BigDecimal.valueOf(10000));
        assertThat(r.balance()).isEqualTo(3000);
        assertThat(r.usable()).isEqualTo(3000);
        assertThat(r.discountAmount()).isEqualByComparingTo("3000");
    }

    @Test
    void calculatePointsUsage_capped_at_50_percent() {
        when(pointClient.getPointBalance("u1")).thenReturn(100000);
        var r = tool.calculatePointsUsage("u1", BigDecimal.valueOf(10000));
        assertThat(r.usable()).isEqualTo(5000);
    }

    @Test
    void calculatePointsUsage_zero_when_cart_zero_or_null() {
        var r1 = tool.calculatePointsUsage("u1", BigDecimal.ZERO);
        var r2 = tool.calculatePointsUsage("u1", null);
        assertThat(r1.usable()).isZero();
        assertThat(r2.usable()).isZero();
    }

    @Test
    void calculateDiscount_percentage_and_fixed_and_unknown() {
        var pct = percent("P", 0.10, BigDecimal.ZERO, null, true, 1, LocalDate.now().plusDays(1));
        var fix = fixed("F", BigDecimal.valueOf(500), BigDecimal.ZERO, true, 1);
        var unknown = new CouponCandidate("id", "U", "FREE_SHIPPING",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null, LocalDate.now().plusDays(1), true, 1);
        var nullType = new CouponCandidate("id", "N", null,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null, LocalDate.now().plusDays(1), true, 1);

        assertThat(CouponOptimizationToolService.calculateDiscount(pct, BigDecimal.valueOf(10000)))
                .isEqualByComparingTo("1000");
        assertThat(CouponOptimizationToolService.calculateDiscount(fix, BigDecimal.valueOf(10000)))
                .isEqualByComparingTo("500");
        assertThat(CouponOptimizationToolService.calculateDiscount(unknown, BigDecimal.valueOf(10000)))
                .isEqualByComparingTo("0");
        assertThat(CouponOptimizationToolService.calculateDiscount(nullType, BigDecimal.valueOf(10000)))
                .isEqualByComparingTo("0");
    }

    @Test
    void calculateDiscount_handles_null_rate_and_amount() {
        var pct = new CouponCandidate("id", "P", "PERCENTAGE", null, null,
                BigDecimal.ZERO, null, LocalDate.now().plusDays(1), true, 1);
        var fix = new CouponCandidate("id", "F", "FIXED_AMOUNT", null, null,
                BigDecimal.ZERO, null, LocalDate.now().plusDays(1), true, 1);
        assertThat(CouponOptimizationToolService.calculateDiscount(pct, BigDecimal.TEN))
                .isEqualByComparingTo("0");
        assertThat(CouponOptimizationToolService.calculateDiscount(fix, BigDecimal.TEN))
                .isEqualByComparingTo("0");
    }

    @Test
    void calculateDiscount_fixed_amount_capped_at_cart() {
        var fix = fixed("F", BigDecimal.valueOf(99999), BigDecimal.ZERO, true, 1);
        assertThat(CouponOptimizationToolService.calculateDiscount(fix, BigDecimal.valueOf(500)))
                .isEqualByComparingTo("500");
    }

    @Test
    void scoreOf_handles_zero_cart() {
        assertThat(CouponOptimizationToolService.scoreOf(BigDecimal.TEN, BigDecimal.ZERO)).isEqualTo(0.0);
        assertThat(CouponOptimizationToolService.scoreOf(BigDecimal.TEN, null)).isEqualTo(0.0);
    }
}
