package com.example.skishop.agent.coupon.tool;

import com.example.skishop.agent.common.dto.CouponCandidate;
import com.example.skishop.agent.common.dto.CouponEvaluation;
import com.example.skishop.agent.coupon.client.CouponServiceClient;
import com.example.skishop.agent.coupon.client.PointServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.lang.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class CouponOptimizationToolService {

    private static final Logger log = LoggerFactory.getLogger(CouponOptimizationToolService.class);

    private final CouponServiceClient couponClient;
    private final PointServiceClient pointClient;

    public CouponOptimizationToolService(CouponServiceClient couponClient,
                                          PointServiceClient pointClient) {
        this.couponClient = couponClient;
        this.pointClient = pointClient;
    }

    @Tool(description = """
            指定ユーザーのカート内容に対して利用可能なクーポン候補を全て取得する。
            最低注文金額・有効期限・適用カテゴリを考慮して絞り込む。
            Evaluator-Optimizer の Generator ステップ。
            """)
    public List<CouponCandidate> getEligibleCoupons(
            @ToolParam(description = "ユーザー ID") String userId,
            @ToolParam(description = "カート合計（円）") BigDecimal cartTotal,
            @ToolParam(description = "カート内カテゴリ（カンマ区切り）") String categories,
            @ToolParam(description = "入力済みクーポンコード（任意）") @Nullable String inputCouponCode) {
        log.info("Tool getEligibleCoupons: userId={}, cartTotal={}", userId, cartTotal);
        if (cartTotal == null) cartTotal = BigDecimal.ZERO;

        List<CouponCandidate> allCoupons = new ArrayList<>(couponClient.getUserCoupons(userId));

        if (inputCouponCode != null && !inputCouponCode.isBlank()) {
            couponClient.findByCouponCode(inputCouponCode).ifPresent(allCoupons::add);
        }

        List<String> categoryList = categories == null || categories.isBlank()
                ? List.of()
                : List.of(categories.split(","));
        BigDecimal total = cartTotal;
        return allCoupons.stream()
                .filter(c -> c.expiresAt() == null || !c.expiresAt().isBefore(LocalDate.now()))
                .filter(c -> c.minimumOrder() == null || total.compareTo(c.minimumOrder()) >= 0)
                .filter(c -> c.applicableCategory() == null
                        || categoryList.contains(c.applicableCategory()))
                .filter(c -> c.usageLimit() > 0)
                .toList();
    }

    @Tool(description = """
            クーポン候補リストの各組み合わせを評価してスコアリングする。
            stackable=true のクーポンは複数適用可能。
            Evaluator-Optimizer の Evaluator ステップ。
            """)
    public List<CouponEvaluation> evaluateCouponCombinations(
            @ToolParam(description = "クーポン候補") List<CouponCandidate> candidates,
            @ToolParam(description = "カート合計（円）") BigDecimal cartTotal) {
        log.info("Tool evaluateCouponCombinations: candidates={}", candidates == null ? 0 : candidates.size());
        List<CouponEvaluation> evaluations = new ArrayList<>();
        if (candidates == null || candidates.isEmpty()
                || cartTotal == null || cartTotal.signum() <= 0) {
            return evaluations;
        }

        for (CouponCandidate coupon : candidates) {
            BigDecimal discount = calculateDiscount(coupon, cartTotal);
            BigDecimal finalTotal = cartTotal.subtract(discount).max(BigDecimal.ZERO);
            double score = scoreOf(discount, cartTotal);
            evaluations.add(new CouponEvaluation(
                    List.of(coupon), discount, finalTotal, score, true,
                    "単品適用: %s → %,d円割引".formatted(coupon.couponCode(), discount.intValue())));
        }

        var stackable = candidates.stream().filter(CouponCandidate::isStackable).toList();
        for (int i = 0; i < stackable.size(); i++) {
            for (int j = i + 1; j < stackable.size(); j++) {
                List<CouponCandidate> combo = List.of(stackable.get(i), stackable.get(j));
                BigDecimal totalDiscount = combo.stream()
                        .map(c -> calculateDiscount(c, cartTotal))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal finalTotal = cartTotal.subtract(totalDiscount).max(BigDecimal.ZERO);
                double score = scoreOf(totalDiscount, cartTotal);
                evaluations.add(new CouponEvaluation(
                        combo, totalDiscount, finalTotal, score, true,
                        "2枚重複適用 → %,d円割引".formatted(totalDiscount.intValue())));
            }
        }
        return evaluations;
    }

    @Tool(description = """
            評価済みクーポン組み合わせの中から最高スコアのものを選択する。
            Evaluator-Optimizer の Optimizer ステップ。
            """)
    public CouponEvaluation selectOptimalCombination(
            @ToolParam(description = "評価済み組み合わせ") List<CouponEvaluation> evaluations) {
        log.info("Tool selectOptimalCombination: count={}", evaluations == null ? 0 : evaluations.size());
        if (evaluations == null || evaluations.isEmpty()) {
            return emptyEvaluation();
        }
        return evaluations.stream()
                .filter(CouponEvaluation::meetsConstraints)
                .max(Comparator.comparingDouble(CouponEvaluation::optimizationScore))
                .orElseGet(this::emptyEvaluation);
    }

    @Tool(description = """
            ユーザーのポイント残高を取得し、カートに使用できるポイント数と割引額を返す。
            ポイント1pt=1円。最大利用率はカート金額の50%まで。
            """)
    public PointUsageResult calculatePointsUsage(
            @ToolParam(description = "ユーザー ID") String userId,
            @ToolParam(description = "カート合計（円）") BigDecimal cartTotal) {
        log.info("Tool calculatePointsUsage: userId={}", userId);
        if (cartTotal == null || cartTotal.signum() <= 0) {
            return new PointUsageResult(0, 0, BigDecimal.ZERO);
        }
        int pointBalance = pointClient.getPointBalance(userId);
        int maxUsablePoints = cartTotal.divide(BigDecimal.valueOf(2), 0, RoundingMode.DOWN).intValue();
        int usablePoints = Math.min(pointBalance, maxUsablePoints);
        return new PointUsageResult(pointBalance, usablePoints, BigDecimal.valueOf(usablePoints));
    }

    public record PointUsageResult(int balance, int usable, BigDecimal discountAmount) {}

    static BigDecimal calculateDiscount(CouponCandidate coupon, BigDecimal cartTotal) {
        return switch (coupon.couponType() == null ? "" : coupon.couponType()) {
            case "PERCENTAGE" -> coupon.discountRate() == null ? BigDecimal.ZERO
                    : cartTotal.multiply(coupon.discountRate()).setScale(0, RoundingMode.HALF_UP);
            case "FIXED_AMOUNT" -> coupon.discountAmount() == null ? BigDecimal.ZERO
                    : coupon.discountAmount().min(cartTotal);
            default -> BigDecimal.ZERO;
        };
    }

    static double scoreOf(BigDecimal discount, BigDecimal cartTotal) {
        if (cartTotal == null || cartTotal.signum() <= 0) return 0.0;
        return discount.divide(cartTotal, 4, RoundingMode.HALF_UP).doubleValue() * 100;
    }

    private CouponEvaluation emptyEvaluation() {
        return new CouponEvaluation(List.of(), BigDecimal.ZERO, BigDecimal.ZERO, 0.0,
                false, "適用可能なクーポンなし");
    }
}
