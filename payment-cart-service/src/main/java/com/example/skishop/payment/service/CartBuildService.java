package com.example.skishop.payment.service;

import com.example.skishop.payment.dto.AddCartItemRequest;
import com.example.skishop.payment.dto.BuildCartRequest;
import com.example.skishop.payment.dto.BuildCartResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Multi-Agent Orchestrator から渡されるカート構築要求を集約し、
 * 小計・割引・合計を正規化したうえで、ユーザーの実カート（{@link CartService}）にも
 * 商品を追加する。これにより /agent から推奨を受けたあと、ユーザーがそのまま
 * 「カート」ページから決済に進める。
 */
@Service
public class CartBuildService {

    private static final Logger log = LoggerFactory.getLogger(CartBuildService.class);

    private final CartService cartService;

    public CartBuildService(CartService cartService) {
        this.cartService = cartService;
    }

    public BuildCartResponse build(BuildCartRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        log.info("BuildCart received: userId={}, orderId={}, items={}",
                request.userId(), request.orderId(), request.items().size());

        BigDecimal subtotal = request.items().stream()
                .map(BuildCartRequest.BuildCartItem::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(0, RoundingMode.HALF_UP);

        BigDecimal coupon = nz(request.couponDiscount());
        BigDecimal point = nz(request.pointDiscount());
        BigDecimal total = subtotal.subtract(coupon).subtract(point).max(BigDecimal.ZERO);

        // ユーザーの実カートに商品を追加（ベストエフォート: 失敗してもプレビュー応答は返す）
        String status = persistToUserCart(request);

        List<BuildCartResponse.BuildCartLine> lines = request.items().stream()
                .map(it -> new BuildCartResponse.BuildCartLine(
                        it.productId(), it.productName(), it.quantity(),
                        it.dynamicUnitPrice(), it.lineTotal()))
                .toList();

        return new BuildCartResponse(
                request.orderId(), request.userId(),
                subtotal, coupon, point, total,
                status, lines, Instant.now());
    }

    /**
     * リクエスト内の各商品をユーザーの実カートに追加する。
     * userId のパースに失敗した場合や個別アイテムの追加に失敗した場合は
     * WARN ログを残してスキップ（エージェントの応答全体は失敗させない）。
     *
     * @return 永続化結果のステータス（{@code CONFIRMED} / {@code CONFIRMED_PARTIAL} / {@code PREVIEW_ONLY}）
     */
    private String persistToUserCart(BuildCartRequest request) {
        UUID userUuid;
        try {
            userUuid = UUID.fromString(request.userId());
        } catch (IllegalArgumentException ex) {
            log.warn("BuildCart: userId が UUID 形式ではないため実カートへの追加をスキップします userId={}",
                    request.userId());
            return "PREVIEW_ONLY";
        }

        int succeeded = 0;
        int failed = 0;
        for (BuildCartRequest.BuildCartItem item : request.items()) {
            try {
                cartService.addItem(userUuid, new AddCartItemRequest(
                        item.productId(),
                        item.productName(),
                        item.quantity(),
                        item.dynamicUnitPrice()));
                succeeded++;
            } catch (RuntimeException ex) {
                failed++;
                log.warn("BuildCart: 実カートへの追加に失敗 userId={} productId={} reason={}",
                        request.userId(), item.productId(), ex.getMessage());
            }
        }
        log.info("BuildCart: 実カート追加結果 userId={} succeeded={} failed={}",
                request.userId(), succeeded, failed);

        if (failed == 0) return "CONFIRMED";
        if (succeeded == 0) return "PREVIEW_ONLY";
        return "CONFIRMED_PARTIAL";
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
