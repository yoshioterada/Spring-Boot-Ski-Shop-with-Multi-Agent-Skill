# Multi-Agent System の既存サービスへの適用ガイド

## 目的

本ドキュメントは、`ai-agent-services/` 配下に新設する Multi-Agent System（Weather, Customer Intent, Equipment Matching, Inventory Monitoring, Dynamic Pricing, Coupon Optimization, Orchestrator の 7 エージェント）を、既存の Spring Boot マイクロサービス群に統合するために、**既存サービスへ加える追加・修正の全項目**を網羅的に整理する。

---

## 0. 重大事項：ポート割り当ての見直し（完了済み）

### 0.1 ポート衝突の発見と解決

設計書執筆時に想定していたポート番号が、既存サービスのポートと衝突していたため、Multi-Agent 側の全設計書を 81xx 系へ再割り当てし、既存サービスを参照する URL は実ポートに修正した。

| 既存サービス | 実ポート | Multi-Agent 設計書での当初の想定 | 状態 |
|------------|---------|--------------------------|------|
| authentication-service | 8080 | — | — |
| user-management-service | **8081** | 8082 と仮定していた | 修正済み ✅ |
| inventory-management-service | **8082** | 8084 と仮定していた | 修正済み ✅ |
| sales-management-service | **8083** | 8087 と仮定していた | 修正済み ✅ |
| payment-cart-service | **8084** | 8085 と仮定していた | 修正済み ✅ |
| point-service | **8085** | 8089 と仮定していた | 修正済み ✅ |
| ai-support-service | **8087** | — | — |
| coupon-service | **8088** | 8086 と仮定していた | 修正済み ✅ |
| mailsend-service | 8089 | — | — |
| **api-gateway-service** | **8090** | weather-agent=8090 と衝突していた | 修正済み ✅ |

### 0.2 新規 Multi-Agent モジュール（ハイブリッド構成）

全 7 Agent は **library jar** として作成し、デフォルトでは `agent-runtime-monolith`（**ポート 8100**）に同梱されて 1 プロセスで起動する（モノリスモード）。負荷増加時に Agent 単位で `*-standalone`（個別ポート 8100〜8106）として分離可能。詳細は [hybrid-deployment-design.md](hybrid-deployment-design.md) を参照。

| モジュール | 種別 | デフォルト動作 | 分散時のポート |
|----------|------|------------|------------|
| `agent-common` | library | 共有 DTO + `WeatherInvoker` | — |
| `weather-agent` | library | `agent-runtime-monolith` に同梱 | 8100（standalone 時）|
| `customer-intent-agent` | library | 同上 | 8101 |
| `equipment-matching-agent` | library | 同上 | 8102 |
| `inventory-monitoring-agent` | library | 同上 | 8103 |
| `dynamic-pricing-agent` | library | 同上 | 8104 |
| `coupon-optimization-agent` | library | 同上 | 8105 |
| `orchestrator-agent` | library | 同上 | 8106 |
| **`agent-runtime-monolith`** | **executable** | **唯一の実行可能 jar（モノリス）** | 8100 |
| `agent-runtime-standalone/*` | executable | 分散時のみ作成 | 各ポート |

**モード切替**: `agents.deployment.mode` プロパティ（`monolith` / `distributed`）。デフォルトは `monolith`。

**修正済み項目（設計書側）**:
- 全 7 設計書の `application.properties` の `server.port` を 81xx 系に修正
- `orchestrator-agent` の各 Worker URL 環境変数デフォルトを `http://localhost:81xx` に修正
- Worker 設計書から参照する既存サービス URL（user-management/inventory/sales/payment-cart/coupon/point）を実ポートに修正
- `orchestrator-agent-design.md` §11.1 Tool↔Endpoint テーブルを新ポートに修正

**残り作業**:
- `docker-compose.yml` の port mapping をハイブリッド構成に合わせる（§11 参照、実装フェーズで適用）

---

## 0.3 Maven 親 POM の追加（新規）

```xml
<!-- ai-agent-services/pom.xml（新規） -->
<?xml version="1.0" encoding="UTF-8"?>
<project>
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.example.skishop</groupId>
        <artifactId>skishop-parent</artifactId>
        <version>1.0.0</version>
        <relativePath>../pom.xml</relativePath>
    </parent>
    <artifactId>ai-agent-services-parent</artifactId>
    <packaging>pom</packaging>

    <modules>
        <module>agent-common</module>
        <module>weather-agent</module>
        <module>customer-intent-agent</module>
        <module>equipment-matching-agent</module>
        <module>inventory-monitoring-agent</module>
        <module>dynamic-pricing-agent</module>
        <module>coupon-optimization-agent</module>
        <module>orchestrator-agent</module>
        <module>agent-runtime-monolith</module>
        <!-- standalone は必要時に追加 -->
    </modules>
</project>
```

ルート `pom.xml` の `<modules>` には `<module>ai-agent-services</module>` を 1 行だけ追加する（§12 参照）。

> **記述**: Spring AI BOM (`spring-ai-bom` 1.0.0) はルート `pom.xml` の `<dependencyManagement>` に既に定義されているため、§12.1 は実作業不要。`spring-boot-maven-plugin` も `<pluginManagement>` に定義済みのため、各 Agent library モジュールで `<plugin>` を宣言しない限り `repackage` は走らない。`agent-runtime-monolith` / `*-standalone` でのみ `<plugin>` を宣言する。

---

## 0.4 AgentRuntimeApplication のメインクラス（新規）

```java
// agent-runtime-monolith/src/main/java/com/example/skishop/agent/runtime/AgentRuntimeApplication.java
package com.example.skishop.agent.runtime;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
    "com.example.skishop.agent.common",
    "com.example.skishop.agent.weather",
    "com.example.skishop.agent.intent",
    "com.example.skishop.agent.equipment",
    "com.example.skishop.agent.inventory",
    "com.example.skishop.agent.pricing",
    "com.example.skishop.agent.coupon",
    "com.example.skishop.agent.orchestrator",
    "com.example.skishop.agent.runtime",
    "com.example.skishop.common"
})
public class AgentRuntimeApplication {
    public static void main(String[] args) {
        SpringApplication.run(AgentRuntimeApplication.class, args);
    }
}
```

各 standalone モジュールも同様に `<Name>StandaloneApplication.java` を持ち、`scanBasePackages` は当該 Agent + `agent-common` + `common-lib` に限定する。

---

## 0.5 `agent-runtime-monolith/src/main/resources/application.yml`（新規）

各 Worker モジュールは `application.properties` を持たず、**最終値はランナー側で与える**（[hybrid-deployment-design.md §4.5](hybrid-deployment-design.md) 参照）。以下はモノリスのデフォルト構成。

```yaml
server:
  port: 8100

spring:
  application:
    name: agent-runtime-monolith
  ai:
    azure:
      openai:
        api-key: ${AZURE_OPENAI_API_KEY}
        endpoint: ${AZURE_OPENAI_ENDPOINT}
        chat:
          options:
            deployment-name: ${AZURE_OPENAI_DEPLOYMENT:gpt-4o}
            temperature: 0.3

# Multi-Agent モード切替（デフォルト: monolith）
agents:
  deployment:
    mode: ${AGENTS_DEPLOYMENT_MODE:monolith}
  web:
    enabled: ${AGENTS_WEB_ENABLED:true}   # 各 Worker Controller の有効/無効

# 既存サービス URL（Worker→既存サービスの内部呼び出し）
external-services:
  user-management:    ${USER_MANAGEMENT_SERVICE_URL:http://localhost:8081}
  inventory:          ${INVENTORY_MANAGEMENT_SERVICE_URL:http://localhost:8082}
  sales:              ${SALES_MANAGEMENT_SERVICE_URL:http://localhost:8083}
  payment-cart:       ${PAYMENT_CART_SERVICE_URL:http://localhost:8084}
  point:              ${POINT_SERVICE_URL:http://localhost:8085}
  coupon:             ${COUPON_SERVICE_URL:http://localhost:8088}

# 内部認証（モノリスでは Worker 側 SecurityConfig が無効化されるため未使用だが、
# 既存サービス呼び出しの X-Internal-Api-Key として常に必要）
internal:
  api-key: ${INTERNAL_API_KEY}

# JWT（Orchestrator Controller のフロント認証用）
jwt:
  secret: ${JWT_SECRET}

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  endpoint:
    health:
      show-details: when-authorized

logging:
  level:
    com.example.skishop.agent: INFO
    org.springframework.ai: INFO
```

> **分散モード時**: `AGENTS_DEPLOYMENT_MODE=distributed` を環境変数で渡すと、各 Worker SecurityConfig（`@ConditionalOnProperty(distributed)`）が有効化され、`InternalApiKeyAuthenticationFilter` が起動する。standalone モジュールはそれぞれ独自の `application.yml` を持ち、`server.port` を 8100〜8106 に振り分ける（各 Agent 設計書 §5 のテンプレートを参照）。

---

## 1. common-lib への追加（全 Worker Agent / 既存サービス共通）

### 1.1 新規追加: `InternalApiKeyAuthenticationFilter`

**作業対象**: [common-lib/src/main/java/com/example/skishop/common/security/](common-lib/src/main/java/com/example/skishop/common/security/)

Orchestrator Agent → Worker Agent 間（**分散モード時のみ**）、および Worker Agent → 既存サービス間の内部呼び出しを認証する共有フィルタ。

**適用範囲**:
- **既存ドメインサービス（user-management, inventory-management, sales-management, payment-cart, point, coupon）**: 必須（Multi-Agent からの REST 呼び出しを認証、モード非依存で常に必要）
- **Multi-Agent の Worker（weather, customer-intent, ...）**: **分散モードのみ**（モノリスでは同一 JVM のため不要）。各 Worker SecurityConfig に `@ConditionalOnProperty(name="agents.deployment.mode", havingValue="distributed")` を付与

```java
// common-lib/src/main/java/com/example/skishop/common/security/InternalApiKeyAuthenticationFilter.java
package com.example.skishop.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class InternalApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(InternalApiKeyAuthenticationFilter.class);
    private static final String HEADER_API_KEY = "X-Internal-Api-Key";
    private static final String HEADER_CALLER  = "X-Caller-Service";

    private final String expectedApiKey;

    public InternalApiKeyAuthenticationFilter(String expectedApiKey) {
        this.expectedApiKey = expectedApiKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        String apiKey = request.getHeader(HEADER_API_KEY);
        if (apiKey != null && !apiKey.isBlank() && apiKey.equals(expectedApiKey)) {
            String caller = request.getHeader(HEADER_CALLER);
            String principal = (caller != null && !caller.isBlank()) ? caller : "internal-service";
            var auth = new UsernamePasswordAuthenticationToken(
                    principal, null,
                    List.of(new SimpleGrantedAuthority("ROLE_AGENT")));
            SecurityContextHolder.getContext().setAuthentication(auth);
            log.debug("Internal API key authenticated. caller={}", principal);
        }
        chain.doFilter(request, response);
    }
}
```

### 1.2 既存 `JwtAuthenticationFilter` への追記不要

[common-lib/src/main/java/com/example/skishop/common/security/JwtAuthenticationFilter.java](common-lib/src/main/java/com/example/skishop/common/security/JwtAuthenticationFilter.java) は既存のまま使用する。InternalApiKeyAuthenticationFilter を **JwtAuthenticationFilter の前** に挿入する（`addFilterBefore`）。

### 1.3 環境変数 `INTERNAL_API_KEY` の発行

- 開発・本番で 32 文字以上のランダム文字列を生成し、全サービスの環境変数 `INTERNAL_API_KEY` に同一値を配布する
- Azure Key Vault / Kubernetes Secret などで管理する
- ローテーション手順を [docs/runbook.md](docs/runbook.md) に追加

---

## 2. user-management-service への追加（必須）

**ポート**: 8081（変更なし）

### 2.1 新規エンドポイント追加: `GET /api/v1/users/{id}/profile`

**目的**: Orchestrator Agent / Customer Intent Agent が「ユーザープロフィール + ティア + 過去購入カテゴリ + ポイント残高」を1回で取得できるようにする。

#### 追加ファイル

```java
// user-management-service/src/main/java/com/example/skishop/usermanagement/dto/UserProfileResponse.java
package com.example.skishop.usermanagement.dto;

import java.util.List;
import java.util.UUID;

public record UserProfileResponse(
        UUID userId,
        String displayName,
        String email,
        String customerTier,             // BRONZE / SILVER / GOLD / PLATINUM
        List<String> purchasedCategories,
        String preferredSkillLevel,      // BEGINNER / INTERMEDIATE / ADVANCED / EXPERT
        Integer pointBalance,
        Integer totalPurchaseCount
) {}
```

#### UserController への追記

```java
@PreAuthorize("#id == authentication.principal or hasAnyRole('ADMIN', 'AGENT')")
@GetMapping("/{id}/profile")
public ResponseEntity<UserProfileResponse> getUserProfile(@PathVariable UUID id) {
    return ResponseEntity.ok(userProfileAggregator.aggregate(id));
}
```

#### 新規サービスクラス: `UserProfileAggregator`

`point-service` (8085) と `sales-management-service` (8083) を集約する。

```java
// user-management-service/src/main/java/com/example/skishop/usermanagement/service/UserProfileAggregator.java
package com.example.skishop.usermanagement.service;

import com.example.skishop.usermanagement.dto.UserProfileResponse;
import com.example.skishop.usermanagement.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

@Service
public class UserProfileAggregator {

    private final UserRepository userRepository;
    private final RestClient pointClient;
    private final RestClient salesClient;

    public UserProfileAggregator(
            UserRepository userRepository,
            @Value("${services.point.base-url}") String pointUrl,
            @Value("${services.sales-management.base-url}") String salesUrl,
            @Value("${services.internal-api-key}") String apiKey) {
        this.userRepository = userRepository;
        this.pointClient = RestClient.builder().baseUrl(pointUrl)
                .defaultHeader("X-Internal-Api-Key", apiKey)
                .defaultHeader("X-Caller-Service", "user-management-service")
                .build();
        this.salesClient = RestClient.builder().baseUrl(salesUrl)
                .defaultHeader("X-Internal-Api-Key", apiKey)
                .defaultHeader("X-Caller-Service", "user-management-service")
                .build();
    }

    public UserProfileResponse aggregate(UUID userId) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        // 1. 顧客ティア + ポイント残高を point-service から取得
        var tierInfo = pointClient.get()
                .uri("/api/v1/tiers/user/{id}", userId)
                .retrieve()
                .body(TierInfoDto.class);

        // 2. 過去購入カテゴリを sales-management-service から取得（新規エンドポイント要）
        var purchaseStats = salesClient.get()
                .uri("/api/v1/sales/customers/{id}/purchase-summary", userId)
                .retrieve()
                .body(PurchaseSummaryDto.class);

        return new UserProfileResponse(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                tierInfo != null ? tierInfo.currentTier() : "BRONZE",
                purchaseStats != null ? purchaseStats.categories() : List.of(),
                user.getPreferredSkillLevel(),
                tierInfo != null ? tierInfo.currentBalance() : 0,
                purchaseStats != null ? purchaseStats.totalCount() : 0);
    }

    private record TierInfoDto(String currentTier, Integer currentBalance) {}
    private record PurchaseSummaryDto(List<String> categories, Integer totalCount) {}
}
```

### 2.2 User モデルへの `preferredSkillLevel` フィールド追加

```java
// user-management-service/src/main/java/com/example/skishop/usermanagement/model/User.java
@Column(name = "preferred_skill_level", length = 20)
private String preferredSkillLevel;  // BEGINNER / INTERMEDIATE / ADVANCED / EXPERT
```

#### マイグレーション SQL（Flyway / Liquibase）

```sql
-- V20260417_001__add_preferred_skill_level.sql
ALTER TABLE users ADD COLUMN preferred_skill_level VARCHAR(20);
COMMENT ON COLUMN users.preferred_skill_level IS 'スキルレベル自己申告: BEGINNER/INTERMEDIATE/ADVANCED/EXPERT';
CREATE INDEX idx_users_skill_level ON users(preferred_skill_level);
```

### 2.3 SecurityConfig に `InternalApiKeyAuthenticationFilter` を登録

```java
// user-management-service/src/main/java/com/example/skishop/usermanagement/config/SecurityConfig.java
.addFilterBefore(new InternalApiKeyAuthenticationFilter(internalApiKey),
        UsernamePasswordAuthenticationFilter.class)
.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
```

### 2.4 application.properties への追記

```properties
services.point.base-url=${POINT_SERVICE_URL:http://localhost:8085}
services.sales-management.base-url=${SALES_MANAGEMENT_SERVICE_URL:http://localhost:8083}
services.internal-api-key=${INTERNAL_API_KEY:}
```

---

## 3. inventory-management-service への追加（必須）

**ポート**: 8082（変更なし）

### 3.1 新規エンドポイント: `POST /api/v1/inventory/check`

**目的**: 複数商品の在庫を一括確認し、AVAILABLE/LOW_STOCK/OUT_OF_STOCK 判定を返す。Inventory Monitoring Agent が呼び出す。

#### 追加 DTO

```java
// inventory-management-service/src/main/java/com/example/skishop/inventory/dto/InventoryCheckRequest.java
public record InventoryCheckRequest(
        @NotEmpty List<String> productIds,
        Integer requiredQuantity   // null の場合は1扱い
) {}

// inventory-management-service/src/main/java/com/example/skishop/inventory/dto/InventoryStatusResponse.java
public record InventoryStatusResponse(
        String productId,
        String productName,
        int stockQuantity,
        String availabilityStatus,         // AVAILABLE / LOW_STOCK / OUT_OF_STOCK
        boolean isReservable,
        String estimatedRestockDate,       // ISO-8601 (在庫切れ時のみ)
        List<String> alternativeProductIds
) {}
```

#### ProductController への追記

```java
@PreAuthorize("hasAnyRole('AGENT', 'ADMIN', 'MANAGER')")
@PostMapping("/inventory/check")
public ResponseEntity<List<InventoryStatusResponse>> checkAvailability(
        @Valid @RequestBody InventoryCheckRequest request) {
    return ResponseEntity.ok(productService.checkAvailability(request));
}
```

### 3.2 新規エンドポイント: `POST /api/v1/inventory/reservations`（注文単位 TTL 予約）

**目的**: 既存の `/inventory/reserve`（単品の在庫減算）とは別に、**注文単位での TTL 30 分の一時ロック**を提供する。Orchestrator Agent が Step5 で呼び出す。

```java
// inventory-management-service/src/main/java/com/example/skishop/inventory/dto/OrderReservationRequest.java
public record OrderReservationRequest(
        @NotBlank String orderId,
        @NotBlank String userId,
        @NotEmpty List<ReservationItem> items,
        @Min(1) @Max(120) int reservationTtlMinutes
) {
    public record ReservationItem(@NotBlank String productId, @Positive int quantity) {}
}

// inventory-management-service/src/main/java/com/example/skishop/inventory/dto/OrderReservationResponse.java
public record OrderReservationResponse(
        String reservationId,
        String orderId,
        boolean isFullyReserved,
        List<String> reservedProductIds,
        List<String> failedProductIds,
        java.time.Instant expiresAt
) {}
```

```java
// ProductController への追記
@PreAuthorize("hasAnyRole('AGENT', 'ADMIN', 'MANAGER')")
@PostMapping("/inventory/reservations")
public ResponseEntity<OrderReservationResponse> reserveForOrder(
        @Valid @RequestBody OrderReservationRequest request) {
    return ResponseEntity.ok(reservationService.reserve(request));
}

@PreAuthorize("hasAnyRole('AGENT', 'ADMIN', 'MANAGER')")
@DeleteMapping("/inventory/reservations/{reservationId}")
public ResponseEntity<Void> releaseReservation(@PathVariable String reservationId) {
    reservationService.release(reservationId);
    return ResponseEntity.noContent().build();
}
```

#### 新規テーブル

```sql
-- V20260417_002__create_inventory_reservations.sql
CREATE TABLE inventory_reservations (
    reservation_id  VARCHAR(36) PRIMARY KEY,
    order_id        VARCHAR(36) NOT NULL,
    user_id         VARCHAR(36) NOT NULL,
    status          VARCHAR(20) NOT NULL,    -- ACTIVE / EXPIRED / RELEASED / CONSUMED
    expires_at      TIMESTAMP   NOT NULL,
    created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_reservations_expires ON inventory_reservations(expires_at) WHERE status = 'ACTIVE';
CREATE INDEX idx_reservations_order ON inventory_reservations(order_id);

CREATE TABLE inventory_reservation_items (
    reservation_id  VARCHAR(36) NOT NULL REFERENCES inventory_reservations(reservation_id) ON DELETE CASCADE,
    product_id      VARCHAR(36) NOT NULL,
    quantity        INTEGER     NOT NULL CHECK (quantity > 0),
    PRIMARY KEY (reservation_id, product_id)
);
```

#### バックグラウンドジョブ（自動失効）

```java
// inventory-management-service/src/main/java/com/example/skishop/inventory/service/ReservationExpirationJob.java
@Component
public class ReservationExpirationJob {
    private final ReservationService reservationService;

    @Scheduled(fixedDelay = 60_000)  // 1 分ごと
    public void releaseExpiredReservations() {
        int released = reservationService.releaseExpired();
        if (released > 0) log.info("Released {} expired reservations", released);
    }
}
```

### 3.3 新規エンドポイント: `GET /api/v1/products/{id}/alternatives`

```java
@GetMapping("/products/{id}/alternatives")
public ResponseEntity<List<String>> findAlternatives(
        @PathVariable String id,
        @RequestParam(required = false) String category,
        @RequestParam(required = false) String skillLevel) {
    return ResponseEntity.ok(productService.findAlternatives(id, category, skillLevel));
}
```

### 3.4 既存 `/products/search` の拡張

既存の検索 API に **`skillLevel` / `maxBudgetYen`** クエリパラメータを追加。Equipment Matching Agent の `searchInventoryCandidates` Tool が利用する。

```java
@GetMapping("/products/search")
public ResponseEntity<Page<ProductResponse>> searchProducts(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String category,
        @RequestParam(required = false) String skillLevel,           // 追加
        @RequestParam(required = false) Integer maxBudgetYen,        // 追加
        @PageableDefault(size = 20) Pageable pageable) { ... }
```

### 3.5 Product モデルへの追加フィールド

```sql
-- V20260417_003__product_skill_metadata.sql
ALTER TABLE products
    ADD COLUMN skill_level_suitability VARCHAR(20),  -- BEGINNER / INTERMEDIATE / ADVANCED / ALL
    ADD COLUMN weather_suitability     VARCHAR(20),  -- POWDER / ALL_CONDITIONS / GROOMED
    ADD COLUMN attributes_json         JSONB;        -- {"length": "165cm", "flex": "medium"}
CREATE INDEX idx_products_skill ON products(skill_level_suitability);
```

### 3.6 SecurityConfig 更新

`InternalApiKeyAuthenticationFilter` を登録（[§1.1](#11-新規追加-internalapikeyauthenticationfilter) 参照）。

---

## 4. payment-cart-service への追加（必須）

**ポート**: 8084（変更なし）

### 4.1 新規エンドポイント: `POST /api/v1/cart/build`

**目的**: Orchestrator Agent が「動的価格 + 適用済みクーポン + ポイント割引」を1リクエストで反映してカート確定する。Step10 専用エンドポイント。

#### 追加 DTO

```java
// payment-cart-service/src/main/java/com/example/skishop/payment/dto/BuildCartRequest.java
public record BuildCartRequest(
        @NotBlank String userId,
        @NotBlank String orderId,
        @NotEmpty List<BuildCartItem> items,
        BigDecimal couponDiscount,
        BigDecimal pointDiscount,
        List<String> appliedCouponCodes,
        Integer appliedPoints
) {
    public record BuildCartItem(
            @NotBlank String productId,
            @NotBlank String productName,
            @Positive int quantity,
            @NotNull BigDecimal dynamicUnitPrice,    // Dynamic Pricing Agent の finalPrice
            @NotNull BigDecimal lineTotal
    ) {}
}

// payment-cart-service/src/main/java/com/example/skishop/payment/dto/BuildCartResponse.java
public record BuildCartResponse(
        String orderId,
        String cartId,
        BigDecimal subtotal,
        BigDecimal couponDiscount,
        BigDecimal pointDiscount,
        BigDecimal totalAmount,
        String status               // BUILT / FAILED
) {}
```

#### CartController への追記

```java
@PreAuthorize("hasAnyRole('AGENT', 'ADMIN')")
@PostMapping("/build")
public ResponseEntity<BuildCartResponse> buildCart(@Valid @RequestBody BuildCartRequest request) {
    return ResponseEntity.ok(cartService.buildCart(request));
}
```

#### サービス実装方針

- `buildCart` は既存カートをクリアし、渡された items を `dynamicUnitPrice` で登録
- `couponDiscount` / `pointDiscount` を `cart_meta` テーブルに保存
- `subtotal - couponDiscount - pointDiscount = totalAmount` を計算
- Kafka に `cart.updated` イベントを発行

### 4.2 Kafka イベント `cart.updated` の発行

```java
@Component
public class CartEventPublisher {
    private final KafkaTemplate<String, CartUpdatedEvent> kafkaTemplate;

    public void publishCartUpdated(BuildCartResponse response) {
        kafkaTemplate.send("cart.updated", response.orderId(),
                new CartUpdatedEvent(response.orderId(), response.totalAmount(), Instant.now()));
    }

    public record CartUpdatedEvent(String orderId, BigDecimal totalAmount, Instant timestamp) {}
}
```

### 4.3 SecurityConfig 更新

`InternalApiKeyAuthenticationFilter` を登録。

---

## 5. coupon-service への追加（必須）

**ポート**: 8088（変更なし、設計書の8086想定を 8088 に修正）

### 5.1 Coupon モデルへのフィールド追加

```sql
-- V20260417_004__coupon_metadata.sql
ALTER TABLE coupons
    ADD COLUMN coupon_type           VARCHAR(20) NOT NULL DEFAULT 'PERCENTAGE',
        -- PERCENTAGE / FIXED_AMOUNT / FREE_SHIPPING / BUNDLE
    ADD COLUMN is_stackable          BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN applicable_category   VARCHAR(50),
    ADD COLUMN minimum_order_amount  NUMERIC(10,2) DEFAULT 0,
    ADD COLUMN usage_limit_per_user  INTEGER;
CREATE INDEX idx_coupons_stackable ON coupons(is_stackable);
CREATE INDEX idx_coupons_type ON coupons(coupon_type);
```

### 5.2 既存 `CouponResponse` DTO の拡張

```java
public record CouponResponse(
        UUID id,
        String code,
        String name,
        String couponType,            // 追加
        BigDecimal discountRate,
        BigDecimal discountAmount,
        BigDecimal minimumOrder,      // 追加
        String applicableCategory,    // 追加
        LocalDate expiresAt,
        boolean isStackable,          // 追加
        Integer remainingUsage        // 追加
) {}
```

### 5.3 既存 `/api/v1/coupons/user/available` のレスポンス拡張

Coupon Optimization Agent が `getEligibleCoupons` Tool で利用する。新フィールドが必須となる。

### 5.4 SecurityConfig 更新

`InternalApiKeyAuthenticationFilter` を登録。`AGENT` ロールでアクセス可能にする。

---

## 6. point-service への追加（推奨）

**ポート**: 8085（変更なし）

### 6.1 既存エンドポイントは流用可能

| 既存エンドポイント | 利用 Agent | 修正要否 |
|-----------------|------------|---------|
| `GET /api/v1/points/balance/{userId}` | Coupon Optimization Agent | 不要 |
| `GET /api/v1/tiers/user/{userId}` | user-management-service (集約) | 不要 |
| `POST /api/v1/points/redeem` | payment-cart-service (Step10) | 不要 |

### 6.2 新規エンドポイント: `POST /api/v1/points/reserve`（任意・推奨）

カート確定までの間ポイントを **仮利用ロック**するための予約 API。実装しない場合は `redeem` を直接呼ぶが、注文キャンセル時に `award` で戻す必要があるため、`reserve`/`commit`/`release` 方式が望ましい。

```java
@PreAuthorize("hasAnyRole('AGENT', 'ADMIN')")
@PostMapping("/points/reserve")
public ResponseEntity<PointReservationResponse> reservePoints(
        @Valid @RequestBody PointReservationRequest request) { ... }

public record PointReservationRequest(
        @NotBlank String userId,
        @NotBlank String orderId,
        @Positive int points,
        @Min(1) @Max(120) int ttlMinutes
) {}
```

### 6.3 SecurityConfig 更新

`InternalApiKeyAuthenticationFilter` を登録。

---

## 7. sales-management-service への追加（必須）

**ポート**: 8083（変更なし）

### 7.1 新規エンドポイント: `GET /api/v1/sales/customers/{id}/purchase-summary`

**目的**: user-management の UserProfileAggregator が顧客の過去購入カテゴリ・購入回数を取得する。

```java
@PreAuthorize("#id == authentication.principal or hasAnyRole('ADMIN', 'AGENT')")
@GetMapping("/sales/customers/{id}/purchase-summary")
public ResponseEntity<PurchaseSummaryResponse> getPurchaseSummary(@PathVariable UUID id) { ... }

public record PurchaseSummaryResponse(
        UUID customerId,
        Integer totalCount,
        BigDecimal totalAmount,
        List<String> categories,         // 過去購入カテゴリ（distinct）
        Instant lastOrderDate
) {}
```

### 7.2 新規エンドポイント: `GET /api/v1/sales/products/{id}/recent-volume`

**目的**: Dynamic Pricing Agent の `applyDemandAdjustment` Tool が直近 N 日の販売数を取得し、需要レベル（VERY_HIGH/HIGH/NORMAL/LOW）を判定する。

```java
@PreAuthorize("hasAnyRole('AGENT', 'ADMIN', 'MANAGER')")
@GetMapping("/sales/products/{id}/recent-volume")
public ResponseEntity<RecentVolumeResponse> getRecentSalesVolume(
        @PathVariable String id,
        @RequestParam(defaultValue = "7") int days) { ... }

public record RecentVolumeResponse(
        String productId,
        int days,
        int totalUnitsSold,
        String demandLevel              // VERY_HIGH / HIGH / NORMAL / LOW
) {}
```

#### 推奨実装

```java
public RecentVolumeResponse getRecentSalesVolume(String productId, int days) {
    int sold = orderRepository.countUnitsSoldSince(productId,
            Instant.now().minus(Duration.ofDays(days)));
    String level = switch (sold) {
        case int n when n >= 50 -> "VERY_HIGH";
        case int n when n >= 20 -> "HIGH";
        case int n when n >= 5  -> "NORMAL";
        default -> "LOW";
    };
    return new RecentVolumeResponse(productId, days, sold, level);
}
```

### 7.3 Kafka イベント `order.created` の発行（既存実装の拡張）

シーケンス図 Step12 の連携基盤。既に `order.created` を発行している場合は不要。未発行の場合は `OrderService.createOrder()` の最後に発行する。

### 7.4 SecurityConfig 更新

`InternalApiKeyAuthenticationFilter` を登録。

---

## 8. ai-support-service への追加（最小修正）

**ポート**: 8087（変更なし）

### 8.1 既存 RecommendationController の流用

Equipment Matching Agent は内部で `ai-support-service` の以下を呼び出すことができる:

| 既存 API | Multi-Agent からの利用 |
|---------|---------------------|
| `GET /api/v1/recommendations/{userId}` | Equipment Matching Agent の補助スコアリング |
| `GET /api/v1/recommendations/similar/{productId}` | Inventory Monitoring Agent の代替製品候補 |
| `POST /api/v1/search/semantic` | Equipment Matching Agent の意味検索 |

### 8.2 SecurityConfig 更新

`InternalApiKeyAuthenticationFilter` を登録。`AGENT` ロールがアクセス可能なエンドポイントに既存 Recommendation/Search API を含める。

### 8.3 Spring AI バージョン整合性確認

Multi-Agent Services は Spring AI 1.0.0 を使用。ai-support-service の Spring AI バージョンが異なる場合は親 pom.xml で BOM 統一する。

---

## 9. api-gateway-service への追加（必須）

**ポート**: 8090（変更なし）

### 9.1 ルーティング追加

Multi-Agent への外部からの入口を Gateway に追加する。`uri` はモノリス／分散で異なるため、**環境変数 `ORCHESTRATOR_URL` で外部化**し、デプロイ時に切替可能にする。

| モード | `ORCHESTRATOR_URL` |
|-------|------------------|
| モノリス（デフォルト） | `http://agent-runtime:8100` |
| 分散 | `http://orchestrator-agent:8106` |

```yaml
# api-gateway-service/src/main/resources/application.yml
spring:
  cloud:
    gateway:
      routes:
        # Orchestrator Agent (顧客向けエントリーポイント)
        - id: orchestrator-agent
          uri: ${ORCHESTRATOR_URL:http://agent-runtime:8100}
          predicates:
            - Path=/api/v1/orchestrator/**
          filters:
            - StripPrefix=0
            - name: CircuitBreaker
              args:
                name: orchestratorCB
                fallbackUri: forward:/fallback/orchestrator

        # Worker Agent への直接アクセス（管理用、ADMIN のみ）
        - id: weather-agent
          uri: http://weather-agent:8100
          predicates:
            - Path=/api/v1/agents/weather/**
        - id: customer-intent-agent
          uri: http://customer-intent-agent:8101
          predicates:
            - Path=/api/v1/agents/intent/**
        - id: equipment-matching-agent
          uri: http://equipment-matching-agent:8102
          predicates:
            - Path=/api/v1/agents/equipment/**
        - id: inventory-monitoring-agent
          uri: http://inventory-monitoring-agent:8103
          predicates:
            - Path=/api/v1/agents/inventory/**
        - id: dynamic-pricing-agent
          uri: http://dynamic-pricing-agent:8104
          predicates:
            - Path=/api/v1/agents/pricing/**
        - id: coupon-optimization-agent
          uri: http://coupon-optimization-agent:8105
          predicates:
            - Path=/api/v1/agents/coupon/**
```

### 9.2 レート制限の追加

オーケストレーターは LLM を呼び出すためコスト・レイテンシが大きい。Gateway 側で Per-User レート制限を導入する。

```yaml
filters:
  - name: RequestRateLimiter
    args:
      redis-rate-limiter.replenishRate: 10    # 1 秒あたり 10 req
      redis-rate-limiter.burstCapacity: 20
      key-resolver: "#{@userKeyResolver}"
```

---

## 10. authentication-service への追加（必須）

**ポート**: 8080（変更なし）

### 10.1 `AGENT` ロールの定義

JWT 発行時のロール一覧に `AGENT` を追加する。エージェント間の REST API 呼び出しで `@PreAuthorize("hasRole('AGENT')")` を機能させる。

```java
// authentication-service/src/main/java/com/example/skishop/auth/model/Role.java
public enum Role {
    USER, MEMBER, ADMIN, MANAGER, AGENT  // AGENT を追加
}
```

ただし、Multi-Agent システムでは **JWT ではなく `X-Internal-Api-Key` で `AGENT` ロールを付与する**設計のため、JWT の `roles` クレームに `AGENT` を含めるケースは限定的（管理 UI からエージェントを直接呼ぶ場合のみ）。

### 10.2 マイグレーション SQL

```sql
-- V20260417_005__seed_agent_role.sql
INSERT INTO roles (id, name, description) VALUES
    (gen_random_uuid(), 'AGENT', 'Multi-Agent System internal role')
ON CONFLICT (name) DO NOTHING;
```

---

## 11. docker-compose.yml への追加（ハイブリッド対応）

モノリスと分散を 2 ファイルで切替可能にする。

### 11.1 `docker-compose.yml`（モノリス・デフォルト）

```yaml
services:
  # ── 既存サービス（省略）──

  agent-runtime:
    build: ./ai-agent-services/agent-runtime-monolith
    ports:
      - "8100:8100"
    environment:
      AZURE_OPENAI_API_KEY: ${AZURE_OPENAI_API_KEY}
      AZURE_OPENAI_ENDPOINT: ${AZURE_OPENAI_ENDPOINT}
      AZURE_OPENAI_DEPLOYMENT: gpt-4o
      JWT_SECRET: ${JWT_SECRET}
      INTERNAL_API_KEY: ${INTERNAL_API_KEY}
      USER_MANAGEMENT_SERVICE_URL: http://user-management-service:8081
      INVENTORY_MANAGEMENT_SERVICE_URL: http://inventory-management-service:8082
      SALES_MANAGEMENT_SERVICE_URL: http://sales-management-service:8083
      PAYMENT_CART_SERVICE_URL: http://payment-cart-service:8084
      POINT_SERVICE_URL: http://point-service:8085
      COUPON_SERVICE_URL: http://coupon-service:8088
      REDIS_HOST: redis
      # AGENTS_DEPLOYMENT_MODE: monolith  ← デフォルトなので省略可
    depends_on:
      - user-management-service
      - inventory-management-service
      - sales-management-service
      - payment-cart-service
      - point-service
      - coupon-service
      - redis
```

### 11.2 `docker-compose.distributed.yml`（オプション・分散）

負荷増加時に `docker compose -f docker-compose.yml -f docker-compose.distributed.yml up` で切替。`agent-runtime` サービスを無効化し、7 個の standalone サービスを起動する。

```yaml
services:
  agent-runtime:
    profiles: ["never"]   # モノリスを無効化

  weather-agent:
    build: ./ai-agent-services/agent-runtime-standalone/weather-standalone
    ports: ["8100:8100"]
    environment:
      AGENTS_DEPLOYMENT_MODE: distributed
      AZURE_OPENAI_API_KEY: ${AZURE_OPENAI_API_KEY}
      AZURE_OPENAI_ENDPOINT: ${AZURE_OPENAI_ENDPOINT}
      INTERNAL_API_KEY: ${INTERNAL_API_KEY}
      JWT_SECRET: ${JWT_SECRET}
      REDIS_HOST: redis
    depends_on:
      - redis

  customer-intent-agent:
    build: ./ai-agent-services/agent-runtime-standalone/customer-intent-standalone
    ports: ["8101:8101"]
    environment:
      AGENTS_DEPLOYMENT_MODE: distributed
      AZURE_OPENAI_API_KEY: ${AZURE_OPENAI_API_KEY}
      AZURE_OPENAI_ENDPOINT: ${AZURE_OPENAI_ENDPOINT}
      INTERNAL_API_KEY: ${INTERNAL_API_KEY}
      JWT_SECRET: ${JWT_SECRET}
      USER_MANAGEMENT_SERVICE_URL: http://user-management-service:8081

  equipment-matching-agent:
    build: ./ai-agent-services/agent-runtime-standalone/equipment-matching-standalone
    ports: ["8102:8102"]
    environment:
      AGENTS_DEPLOYMENT_MODE: distributed
      AZURE_OPENAI_API_KEY: ${AZURE_OPENAI_API_KEY}
      AZURE_OPENAI_ENDPOINT: ${AZURE_OPENAI_ENDPOINT}
      INTERNAL_API_KEY: ${INTERNAL_API_KEY}
      INVENTORY_SERVICE_URL: http://inventory-management-service:8082
      WEATHER_AGENT_URL: http://weather-agent:8100

  inventory-monitoring-agent:
    build: ./ai-agent-services/agent-runtime-standalone/inventory-monitoring-standalone
    ports: ["8103:8103"]
    environment:
      AGENTS_DEPLOYMENT_MODE: distributed
      AZURE_OPENAI_API_KEY: ${AZURE_OPENAI_API_KEY}
      AZURE_OPENAI_ENDPOINT: ${AZURE_OPENAI_ENDPOINT}
      INTERNAL_API_KEY: ${INTERNAL_API_KEY}
      INVENTORY_MANAGEMENT_SERVICE_URL: http://inventory-management-service:8082

  dynamic-pricing-agent:
    build: ./ai-agent-services/agent-runtime-standalone/dynamic-pricing-standalone
    ports: ["8104:8104"]
    environment:
      AGENTS_DEPLOYMENT_MODE: distributed
      AZURE_OPENAI_API_KEY: ${AZURE_OPENAI_API_KEY}
      AZURE_OPENAI_ENDPOINT: ${AZURE_OPENAI_ENDPOINT}
      INTERNAL_API_KEY: ${INTERNAL_API_KEY}
      INVENTORY_MANAGEMENT_SERVICE_URL: http://inventory-management-service:8082
      SALES_MANAGEMENT_SERVICE_URL: http://sales-management-service:8083
      WEATHER_AGENT_URL: http://weather-agent:8100

  coupon-optimization-agent:
    build: ./ai-agent-services/agent-runtime-standalone/coupon-optimization-standalone
    ports: ["8105:8105"]
    environment:
      AGENTS_DEPLOYMENT_MODE: distributed
      AZURE_OPENAI_API_KEY: ${AZURE_OPENAI_API_KEY}
      AZURE_OPENAI_ENDPOINT: ${AZURE_OPENAI_ENDPOINT}
      INTERNAL_API_KEY: ${INTERNAL_API_KEY}
      COUPON_SERVICE_URL: http://coupon-service:8088
      POINT_SERVICE_URL: http://point-service:8085

  orchestrator-agent:
    build: ./ai-agent-services/agent-runtime-standalone/orchestrator-standalone
    ports: ["8106:8106"]
    environment:
      AGENTS_DEPLOYMENT_MODE: distributed
      AZURE_OPENAI_API_KEY: ${AZURE_OPENAI_API_KEY}
      AZURE_OPENAI_ENDPOINT: ${AZURE_OPENAI_ENDPOINT}
      INTERNAL_API_KEY: ${INTERNAL_API_KEY}
      JWT_SECRET: ${JWT_SECRET}
      WEATHER_AGENT_URL: http://weather-agent:8100
      CUSTOMER_INTENT_AGENT_URL: http://customer-intent-agent:8101
      EQUIPMENT_MATCHING_AGENT_URL: http://equipment-matching-agent:8102
      INVENTORY_MONITORING_AGENT_URL: http://inventory-monitoring-agent:8103
      DYNAMIC_PRICING_AGENT_URL: http://dynamic-pricing-agent:8104
      COUPON_OPTIMIZATION_AGENT_URL: http://coupon-optimization-agent:8105
      USER_MANAGEMENT_SERVICE_URL: http://user-management-service:8081
      PAYMENT_CART_SERVICE_URL: http://payment-cart-service:8084
    depends_on:
      - weather-agent
      - customer-intent-agent
      - equipment-matching-agent
      - inventory-monitoring-agent
      - dynamic-pricing-agent
      - coupon-optimization-agent
      - user-management-service
      - payment-cart-service
```

### 11.3 部分分離例（段階的移行）

Equipment Matching と Dynamic Pricing だけ分離し、その他はモノリスのまま使う例:

```yaml
# docker-compose.partial.yml
services:
  agent-runtime:                                  # 残り 5 Agent + Orchestrator
    image: agent-runtime-monolith
    ports: ["8100:8100"]
    environment:
      AGENTS_DEPLOYMENT_MODE: distributed         # 分離 Agent を REST 呼び出し
      EQUIPMENT_MATCHING_AGENT_URL: http://equipment-matching:8102
      DYNAMIC_PRICING_AGENT_URL: http://dynamic-pricing:8104
      WEATHER_AGENT_URL: http://localhost:8100    # self
      # 他の Worker URL も self に向ける

  equipment-matching:
    image: equipment-matching-standalone
    ports: ["8102:8102"]

  dynamic-pricing:
    image: dynamic-pricing-standalone
    ports: ["8104:8104"]
```

> **TODO**: 部分分離時の Worker 単位 Local/Remote 切替は、`agents.workers.<name>.mode` プロパティで個別指定可能にする拡張が必要（[hybrid-deployment-design.md §6.3](hybrid-deployment-design.md) 参照）。

### 11.4 既存サービスへの環境変数追加

全既存サービスの `environment:` セクションに `INTERNAL_API_KEY: ${INTERNAL_API_KEY}` を追記する。

### 11.5 `.env.example` への追加

```bash
# .env.example
INTERNAL_API_KEY=replace-with-32-char-random-string-xxxxxxxxx
AZURE_OPENAI_API_KEY=
AZURE_OPENAI_ENDPOINT=
AZURE_OPENAI_DEPLOYMENT=gpt-4o
# AGENTS_DEPLOYMENT_MODE=monolith  # デフォルト。distributed にしたい場合のみ設定
```

---

## 12. ルート pom.xml への追加（必須）

`<modules>` に `ai-agent-services`（親 POM）を 1 行追加する。個別 Agent モジュールは `ai-agent-services/pom.xml`（§0.3）が子モジュールとして統括する。

```xml
<!-- pom.xml -->
<modules>
    <!-- 既存モジュール -->
    <module>common-lib</module>
    <module>authentication-service</module>
    <module>user-management-service</module>
    <module>inventory-management-service</module>
    <module>sales-management-service</module>
    <module>payment-cart-service</module>
    <module>point-service</module>
    <module>ai-support-service</module>
    <module>coupon-service</module>
    <module>mailsend-service</module>
    <module>api-gateway-service</module>

    <!-- Multi-Agent 親モジュール（追加） -->
    <module>ai-agent-services</module>
</modules>
```

### 12.1 Spring AI BOM の親 pom への追加

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>1.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### 12.2 `spring-boot-maven-plugin` の pluginManagement

親 POM に `<pluginManagement>` を定義し、`agent-runtime-monolith` および `*-standalone` モジュールでのみ `<plugin>` を有効化する。各 Agent library モジュールでは plugin を宣言しないか、`repackage` execution を `<skip>true</skip>` する（[hybrid-deployment-design.md §2.2](hybrid-deployment-design.md)）。

---

## 13. 監視・運用への追加

### 13.1 Prometheus / Grafana

**作業対象**: [monitoring/prometheus/](monitoring/prometheus/), [monitoring/grafana/](monitoring/grafana/)

#### Prometheus scrape 設定追加（モード別）

**モノリス用** `monitoring/prometheus/prometheus.monolith.yml`:

```yaml
scrape_configs:
  - job_name: 'agent-runtime'
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ['agent-runtime:8100']
```

**分散用** `monitoring/prometheus/prometheus.distributed.yml`:

```yaml
scrape_configs:
  - job_name: 'multi-agents'
    metrics_path: /actuator/prometheus
    static_configs:
      - targets:
          - weather-agent:8100
          - customer-intent-agent:8101
          - equipment-matching-agent:8102
          - inventory-monitoring-agent:8103
          - dynamic-pricing-agent:8104
          - coupon-optimization-agent:8105
          - orchestrator-agent:8106
```

Grafana ダッシュボードは `application` ラベル（`management.metrics.tags.application=${spring.application.name}`）で Agent ごとのメトリクスを識別する。モノリスでも Spring AI Micrometer メトリクスに `tool.name` / `chat.client.name` タグが付与されるため Agent 別の可視化は可能。

#### Grafana ダッシュボード新規作成

- **Multi-Agent Overview**: エージェント別レスポンスタイム、エラー率、Tool 呼び出し回数
- **LLM Usage**: Azure OpenAI トークン消費量・コスト推定（Spring AI Micrometer メトリクス）
- **Orchestrator Workflow**: 各 Phase の実行時間内訳

### 13.2 Runbook 追加

**作業対象**: [docs/runbook.md](docs/runbook.md)

以下のシナリオを追記:

- **`INTERNAL_API_KEY` ローテーション手順**
- **Worker Agent 障害時の Orchestrator 動作**（Spring AI Retry → Fallback）
- **Azure OpenAI クォータ枯渇時の対応**
- **在庫予約 (TTL) が滞留した場合の手動解放手順**

### 13.3 Rollback Plan 更新

**作業対象**: [docs/rollback-plan.md](docs/rollback-plan.md)

- Multi-Agent 7 サービスは独立してロールバック可能
- Orchestrator のみダウンさせれば、既存 EC フローは継続稼働できる設計を明記

---

## 14. infra/terraform への追加

### 14.1 Azure リソース追加

**作業対象**: [infra/terraform/](infra/terraform/)

- Azure Container Apps リソースを **module 化**し、`var.deployment_mode`（`monolith` / `distributed`）で切替可能にする:
  - **モノリス**: `agent-runtime` Container App 1 つ（8 Agent 分を考慮し CPU/メモリを増量、例: 2 vCPU / 4 GiB）
  - **分散**: 7 Container Apps
- Azure OpenAI Service のデプロイメント `gpt-4o` のクォータ確保（モノリス時は 1 アプリに集中するため TPM/RPM 上限見直し必須）
- Azure Key Vault に `INTERNAL_API_KEY` シークレット追加
- Azure Cache for Redis（Weather Agent のキャッシュ用）

```hcl
# infra/terraform/main.tf
variable "deployment_mode" {
  type    = string
  default = "monolith"
}

module "agent_runtime" {
  source          = "./modules/agent-runtime"
  deployment_mode = var.deployment_mode
  # monolith 時: 1 Container App / distributed 時: 7 Container Apps
}
```

### 14.2 ネットワーク設定

- Multi-Agent サービスは **VNet 内部ネットワークのみ**で通信（外部公開は Orchestrator の `/api/v1/orchestrator/**` のみ、Gateway 経由）
- モノリス時は `agent-runtime:8100` が単一ターゲット、分散時は `orchestrator-agent:8106` がターゲット
- Worker Agent への直接アクセスは Gateway の管理者ロール限定

---

## 15. テスト計画への追加

### 15.1 既存サービスへのコントラクトテスト追加

各既存サービスに、Multi-Agent から呼び出される新規エンドポイントの **コントラクトテスト**（Spring Cloud Contract or Pact）を追加する。

| サービス | コントラクト対象エンドポイント |
|---------|---------------------------|
| user-management-service | `GET /api/v1/users/{id}/profile` |
| inventory-management-service | `POST /api/v1/inventory/check`, `POST /api/v1/inventory/reservations` |
| payment-cart-service | `POST /api/v1/cart/build` |
| sales-management-service | `GET /api/v1/sales/products/{id}/recent-volume`, `/customers/{id}/purchase-summary` |
| coupon-service | `GET /api/v1/coupons/user/available`（拡張フィールド） |

### 15.2 E2E テスト追加

**作業対象**: [load-tests/](load-tests/)

- Orchestrator `/recommend` への E2E シナリオ（11 ステップ全実行）
- 各エージェント単体での負荷テスト（Azure OpenAI コスト見積もり）

---

## 16. 作業優先順位（推奨実施順）

| 優先度 | 作業項目 | 担当 | 完了条件 |
|------|---------|-----|---------|
| **P0** | ポート番号の見直し（§0）と全設計書修正 | アーキテクト | 全設計書の port が衝突しないこと |
| **P0** | `common-lib/InternalApiKeyAuthenticationFilter` 実装（§1） | プラットフォーム | 単体テスト Pass |
| **P0** | `INTERNAL_API_KEY` の発行・配布手順整備（§1.3） | SRE | KeyVault に登録 |
| **P1** | user-management `/profile` API 追加（§2） | バックエンド | コントラクトテスト Pass |
| **P1** | inventory `/check`, `/reservations` API 追加（§3.1, §3.2） | バックエンド | コントラクトテスト Pass |
| **P1** | payment-cart `/build` API 追加（§4） | バックエンド | コントラクトテスト Pass |
| **P1** | coupon モデル拡張（§5） | バックエンド | DB マイグレーション完了 |
| **P1** | sales `/recent-volume`, `/purchase-summary` API 追加（§7） | バックエンド | コントラクトテスト Pass |
| **P2** | api-gateway ルーティング追加（§9） | バックエンド | Gateway 経由で疎通確認 |
| **P2** | docker-compose 追加（§11） | DevOps | 全サービス起動成功 |
| **P2** | authentication `AGENT` ロール追加（§10） | バックエンド | JWT に AGENT 付与可能 |
| **P3** | inventory `/products/search` 拡張・モデル属性追加（§3.4, §3.5） | バックエンド | Equipment Agent 連携可能 |
| **P3** | point `/reserve` API 追加（§6.2、任意） | バックエンド | キャンセル時に戻せる |
| **P3** | 監視・Runbook・Rollback Plan 更新（§13） | SRE | レビュー完了 |
| **P3** | terraform / infra 更新（§14） | SRE | 本番環境にデプロイ可能 |
| **P3** | E2E / コントラクトテスト追加（§15） | QA | カバレッジ 80% 以上 |

---

## 17. 既存サービス影響度サマリー

| サービス | 修正規模 | 後方互換性 | DB 変更 | 必須/任意 |
|---------|---------|----------|---------|----------|
| common-lib | 中（新規 Filter 追加） | 完全 | なし | **必須** |
| user-management-service | 中（新規 API + 集約 + フィールド追加） | 完全 | あり | **必須** |
| inventory-management-service | 大（複数新規 API + 新規テーブル + モデル拡張） | 完全 | あり | **必須** |
| payment-cart-service | 中（新規 API + Kafka 発行） | 完全 | 軽微 | **必須** |
| coupon-service | 中（モデル拡張 + DTO 拡張） | DTO は追加フィールドのみで互換 | あり | **必須** |
| sales-management-service | 中（新規 API 2 件） | 完全 | なし | **必須** |
| point-service | 小（既存 API 流用 + 任意の `/reserve` 追加） | 完全 | 軽微 | 任意 |
| ai-support-service | 極小（SecurityConfig のみ） | 完全 | なし | 任意 |
| api-gateway-service | 中（ルーティング + RateLimiter） | 完全 | なし | **必須** |
| authentication-service | 小（`AGENT` ロール追加） | 完全 | あり | **必須** |
| mailsend-service | なし | — | なし | — |

### 17.1 ハイブリッドモード別の必要性マトリクス

| 変更項目 | モノリス時 | 分散時 |
|---------|----------|---------|
| common-lib `InternalApiKeyAuthenticationFilter` 追加 | 必須（既存サービス側のため） | 必須 |
| 既存サービス SecurityConfig への Filter 登録 | 必須 | 必須 |
| 既存サービスの新規エンドポイント（`/profile`, `/check`, etc.） | 必須 | 必須 |
| 各 Worker `SecurityConfig` の `@ConditionalOnProperty(distributed)` | 記述は必須（不活性化） | 必須（活性化） |
| `WorkerAgentRestClient` Bean 化 | 不要（`@ConditionalOnProperty` で除外） | 必須 |
| `agent-runtime-monolith` モジュール | **必須**（基本構成） | 不要 |
| `agent-runtime-standalone/*` モジュール | 不要 | 必須 |
| docker-compose.yml | モノリス版 | 分散版を追加 |
| Prometheus スクレイプ設定 | `prometheus.monolith.yml` | `prometheus.distributed.yml` |
| Terraform Container App | 1 インスタンス（高 spec） | 7 インスタンス |
| api-gateway `ORCHESTRATOR_URL` | `http://agent-runtime:8100` | `http://orchestrator-agent:8106` |

---

## 18. 参照ドキュメント

- [orchestrator-agent-design.md](ai-agent-services/orchestrator-agent-design.md)（特に §11 整合性検証マトリクス）
- [weather-agent-design.md](ai-agent-services/weather-agent-design.md)
- [customer-intent-agent-design.md](ai-agent-services/customer-intent-agent-design.md)
- [equipment-matching-agent-design.md](ai-agent-services/equipment-matching-agent-design.md)
- [inventory-monitoring-agent-design.md](ai-agent-services/inventory-monitoring-agent-design.md)
- [dynamic-pricing-agent-design.md](ai-agent-services/dynamic-pricing-agent-design.md)
- [coupon-optimization-agent-design.md](ai-agent-services/coupon-optimization-agent-design.md)
- [docker-compose.yml](docker-compose.yml)
- [pom.xml](pom.xml)
