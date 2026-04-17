package com.example.skishop.agent.orchestrator.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * payment-cart-service へのカート構築呼び出し（モード非依存）。
 */
public class PaymentCartClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentCartClient.class);
    private final RestClient restClient;

    @org.springframework.beans.factory.annotation.Autowired
    public PaymentCartClient(
            @Value("${services.payment-cart.base-url:http://localhost:8084}") String baseUrl,
            @Value("${services.internal-api-key:}") String apiKey) {
        this(RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Internal-Api-Key", apiKey == null ? "" : apiKey)
                .defaultHeader("X-Caller-Service", "orchestrator-agent")
                .build());
    }

    PaymentCartClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * payment-cart-service にカートを構築・確定する。
     * @return BuildCartResult を Map で返す（@Tool が JSON 文字列化する）。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> buildCart(String userId, String orderId, String cartItemsEncoded,
                                          BigDecimal couponDiscount, BigDecimal pointDiscount) {
        log.info("PaymentCart buildCart: userId={}, orderId={}", userId, orderId);
        Map<String, Object> body = Map.of(
                "userId", userId,
                "orderId", orderId,
                "items", parseCartItems(cartItemsEncoded),
                "couponDiscount", couponDiscount == null ? BigDecimal.ZERO : couponDiscount,
                "pointDiscount", pointDiscount == null ? BigDecimal.ZERO : pointDiscount);
        try {
            Map<String, Object> result = restClient.post()
                    .uri("/api/v1/cart/build").body(body)
                    .retrieve().body(Map.class);
            return result == null ? Map.of() : result;
        } catch (RestClientException e) {
            log.warn("payment-cart 呼び出し失敗 orderId={}: {}", orderId, e.getMessage());
            return Map.of("orderId", orderId, "status", "FAILED", "error", e.getMessage());
        }
    }

    static List<Map<String, Object>> parseCartItems(String encoded) {
        if (encoded == null || encoded.isBlank()) return List.of();
        return Arrays.stream(encoded.split(";"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    String[] p = s.split("\\|");
                    if (p.length < 4) {
                        throw new IllegalArgumentException(
                                "cartItemsEncoded format invalid (productId|name|qty|unitPrice expected): " + s);
                    }
                    Map<String, Object> item = new HashMap<>();
                    item.put("productId", p[0].trim());
                    item.put("productName", p[1].trim());
                    int qty = Integer.parseInt(p[2].trim());
                    BigDecimal unit = new BigDecimal(p[3].trim());
                    item.put("quantity", qty);
                    item.put("dynamicUnitPrice", unit);
                    item.put("lineTotal", unit.multiply(BigDecimal.valueOf(qty)));
                    return item;
                })
                .toList();
    }
}
