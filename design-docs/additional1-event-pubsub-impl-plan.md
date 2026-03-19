# EventPublisher 実装詳細設計書・実装計画

## 1. 現状分析

### 1.1 現行 EventPublishingService の実装状況

| サービス | パッケージ | API パターン | DomainEvent 使用 | メソッド数 | 呼び出し元 |
|---------|----------|-------------|-----------------|-----------|-----------|
| authentication-service | `auth.service` | ドメイン特化メソッド群 | ✅ あり | 7 | AuthenticationService (7箇所) |
| user-management-service | `usermanagement.service` | 汎用 `publish(DomainEvent<?>)` | ✅ あり | 1 | UserService (7箇所) |
| sales-management-service | `sales.service` | 汎用 `publish(DomainEvent<?>)` | ✅ あり | 1 | OrderService (6箇所) |
| payment-cart-service | `payment.service` | 汎用 `publish(DomainEvent<?>)` | ✅ あり | 1 | PaymentService (3箇所), CartService (4箇所) |
| inventory-management-service | `inventory.service` | 汎用 `publish(DomainEvent<?>)` | ✅ あり | 1 | ProductService (8箇所), CategoryService |
| point-service | `point.service` | ドメイン特化メソッド群 | ❌ なし | 4 | PointService (4箇所) |
| coupon-service | `coupon.service` | ドメイン特化メソッド群 | ❌ なし | 5 | CouponService (3箇所), CampaignService (1箇所) |

### 1.2 問題の分類

#### Problem 1: DRY 違反（A-H-03）
7 サービスに同じ「ログ出力のみ」の EventPublishingService が重複。変更時に 7 箇所を同期修正する必要がある。

#### Problem 2: API インターフェース不統一
- **パターン A（汎用型）**: `publish(DomainEvent<?>)` — user-mgmt, sales, payment-cart, inventory の 4 サービス
- **パターン B（ドメイン特化 + DomainEvent）**: `publishXxx(DomainEvent<?>)` — authentication-service（7 特化メソッド）
- **パターン C（ドメイン特化 + プリミティブ引数）**: `publishXxx(String, String, ...)` — point-service（4 メソッド）, coupon-service（5 メソッド）

#### Problem 3: Kafka スタブの決定未記録（A-H-01）
全サービスの `pom.xml` に `spring-cloud-stream-binder-kafka` が依存に含まれているが、`application.properties` に一切の Spring Cloud Stream / Kafka 設定がない。現在は全て `log.info()` のみ。

### 1.3 既存のインフラ資産

| 資産 | 状態 | 場所 |
|------|------|------|
| `DomainEvent<T>` レコード | ✅ 存在・利用中 | `common-lib/.../common/event/DomainEvent.java` |
| `spring-cloud-stream-binder-kafka` 依存 | ✅ 8/9 サービスの pom.xml に宣言済み | 全サービス（gateway 除く） |
| Kafka ブローカー設定 | ❌ なし | application.properties に設定なし |
| Spring Cloud Stream binding 設定 | ❌ なし | — |
| イベントスキーマ定義 | ❌ なし | ペイロードは各サービスのインナーレコード |

---

## 2. 設計方針

### 2.1 設計原則

1. **Strategy パターン + インターフェース抽象化**: `EventPublisher` インターフェースを common-lib に定義し、実装を差し替え可能にする
2. **DomainEvent<T> への統一**: 全サービスが `DomainEvent<T>` を経由してイベントを発行する。パターン C（プリミティブ引数）はサービス側のアダプター層で `DomainEvent` に変換する
3. **段階的移行（Strangler Fig）**: Phase 1 で LoggingEventPublisher（現在と同等）に統一 → Phase 2 で Spring Cloud Stream 実装に差し替え
4. **ゼロダウンタイム移行**: 既存テストを壊さない。既存の Mock パターン（`@Mock EventPublishingService`）が新インターフェースに自然に移行できる設計
5. **Outbox パターン準備**: 将来の Transactional Outbox パターン導入に備えたインターフェース設計

### 2.2 アーキテクチャ決定

```
┌─────────────────────────────────────────────────────────────────┐
│                     common-lib                                   │
│                                                                  │
│  ┌──────────────────────┐     ┌──────────────────────────────┐  │
│  │   «interface»         │     │   DomainEvent<T>              │  │
│  │   EventPublisher      │     │   (既存レコード)               │  │
│  │                       │     │   eventId, eventType,         │  │
│  │  + publish(event)     │     │   timestamp, producer,        │  │
│  │                       │     │   payload, correlationId,     │  │
│  └──────────┬───────────┘     │   version                     │  │
│             │                  └──────────────────────────────┘  │
│             │                                                    │
│  ┌──────────▼───────────┐                                       │
│  │ LoggingEventPublisher │  ← Phase 1 デフォルト実装             │
│  │ (@ConditionalOnMissing│                                      │
│  │  Bean)                │                                       │
│  └───────────────────────┘                                       │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│              各サービス（Phase 2 以降に追加）                      │
│                                                                  │
│  ┌───────────────────────┐                                       │
│  │ SpringCloudStream      │  ← Phase 2 Kafka 実装               │
│  │ EventPublisher         │                                      │
│  │ (実装は各サービスの     │                                      │
│  │  binding 設定で制御)    │                                      │
│  └───────────────────────┘                                       │
│                                                                  │
│  ┌───────────────────────┐                                       │
│  │ OutboxEventPublisher   │  ← Phase 3 Outbox パターン          │
│  │ (テーブル書込み →      │                                      │
│  │  Debezium/Polling)     │                                      │
│  └───────────────────────┘                                       │
└─────────────────────────────────────────────────────────────────┘
```

### 2.3 イベントトピック設計

```
                    skishop.events
                         │
        ┌────────────────┼────────────────┐
        │                │                │
   auth.events     user.events     order.events  ...
        │                │                │
  USER_REGISTERED  user.created    OrderCreated
  USER_DELETED     user.updated    OrderStatusUpdated
  USER_AUTHENTICATED user.deleted  OrderCancelled
  USER_LOGGED_OUT  user.verified   ShipmentCreated
  LOGIN_FAILED     user.role_changed ReturnRequested
  ACCOUNT_LOCKED   user.password_changed ReturnProcessed
  PASSWORD_CHANGED

   inventory.events    payment.events    point.events     coupon.events
        │                   │                │                │
  ProductCreated      (決済イベント)   points.awarded    coupon.created
  InventoryReserved                   points.redeemed   coupon.validated
  InventoryUpdated                    points.transferred coupon.redeemed
  InventoryLow                        tier.upgraded     campaign.activated
  InventoryOutOfStock                                   campaign.completed
  PriceUpdated
```

---

## 3. 詳細設計

### 3.1 common-lib に追加するクラス

#### 3.1.1 `EventPublisher` インターフェース

```java
package com.example.skishop.common.event;

/**
 * ドメインイベントの発行を抽象化するインターフェース。
 * <p>
 * 実装は LoggingEventPublisher（スタブ）、SpringCloudStreamEventPublisher（Kafka）、
 * OutboxEventPublisher（Transactional Outbox）に差し替え可能。
 * </p>
 *
 * @see DomainEvent
 * @see LoggingEventPublisher
 */
public interface EventPublisher {

    /**
     * ドメインイベントを同期的に発行する。
     * トランザクション境界内で呼び出されることを想定。
     *
     * @param event 発行するドメインイベント（null 不可）
     * @throws EventPublishException イベント発行に失敗した場合
     */
    <T> void publish(DomainEvent<T> event);
}
```

#### 3.1.2 `EventPublishException`（実行時例外）

> **注意**: `ApplicationException` は sealed クラスであり `permits` 句に限定された型のみ継承可能。
> `EventPublishException` はインフラ層の例外でありビジネス例外階層とは責務が異なるため、
> `RuntimeException` を直接継承する。

```java
package com.example.skishop.common.event;

public class EventPublishException extends RuntimeException {

    public EventPublishException(String message) {
        super(message);
    }

    public EventPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

#### 3.1.3 `LoggingEventPublisher`（デフォルト実装）

```java
package com.example.skishop.common.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * EventPublisher のログ出力のみの実装。
 * Kafka 統合前の開発・テスト用スタブ。
 * <p>
 * 各サービスで具体的な EventPublisher Bean が定義されていない場合に
 * フォールバックとして使用される。
 * </p>
 */
public class LoggingEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingEventPublisher.class);

    @Override
    public <T> void publish(DomainEvent<T> event) {
        log.info("Event published: type={}, eventId={}, producer={}, correlationId={}",
                event.eventType(), event.eventId(), event.producer(), event.correlationId());
    }
}
```

#### 3.1.4 `EventPublisherAutoConfiguration`（自動構成）

```java
package com.example.skishop.common.event;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EventPublisherAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(EventPublisher.class)
    public EventPublisher loggingEventPublisher() {
        return new LoggingEventPublisher();
    }
}
```

### 3.2 サービス別移行設計

#### パターン A サービス（汎用 `publish` 利用 — 4 サービス）

**対象**: user-management-service, sales-management-service, payment-cart-service, inventory-management-service

**移行方法**: 最小影響。`EventPublishingService` を削除し、呼び出し元を `EventPublisher` に置換するだけ。

```java
// Before（各サービスの Service クラス）
private final EventPublishingService eventPublishingService;
eventPublishingService.publish(DomainEvent.create(...));

// After
private final EventPublisher eventPublisher;
eventPublisher.publish(DomainEvent.create(...));
```

**変更ファイル一覧**:

| サービス | 削除ファイル | 変更ファイル（import + フィールド + コンストラクタ） |
|---------|-----------|----------------------------------------------|
| user-management-service | `EventPublishingService.java` | `UserService.java` |
| sales-management-service | `EventPublishingService.java` | `OrderService.java` |
| payment-cart-service | `EventPublishingService.java` | `PaymentService.java` (3箇所), `CartService.java` (4箇所) |
| inventory-management-service | `EventPublishingService.java` | `ProductService.java` (8箇所), `CategoryService.java` |

**テスト影響**: `@Mock EventPublishingService` → `@Mock EventPublisher` に変更。`verify(eventPublisher).publish(any())` は同一シグネチャのため互換。

**テスト変更ファイル一覧**:

| サービス | テストファイル | 変更内容 |
|---------|-------------|--------|
| user-management-service | `UserServiceTest.java` | Mock 型 + verify 差替 |
| sales-management-service | `OrderServiceTest.java` | Mock 型 + verify 差替 |
| payment-cart-service | `PaymentServiceTest.java` | Mock 型 + verify 差替 |
| payment-cart-service | `CartServiceTest.java` | Mock 型 + verify 差替 |
| inventory-management-service | `ProductServiceTest.java` | Mock 型 + verify 差替 |
| inventory-management-service | `CategoryServiceTest.java` | Mock 型 + verify 差替 |

#### パターン B サービス（ドメイン特化 + DomainEvent — authentication-service）

**現状**: 7 つのドメイン特化メソッド（`publishUserRegistered`, `publishUserDeleted` 等）。全て内部で `log.info()` のみ。呼び出し元は既に `DomainEvent.create(...)` を構築して渡している。

**移行方法**: EventPublishingService を削除し、AuthenticationService が直接 `EventPublisher.publish()` を呼ぶよう変更。

```java
// Before
eventPublishingService.publishUserRegistered(DomainEvent.create(
    "USER_REGISTERED", "authentication-service", payload));

// After
eventPublisher.publish(DomainEvent.create(
    "USER_REGISTERED", "authentication-service", payload));
```

**変更ファイル**:
- 削除: `auth/service/EventPublishingService.java`
- 変更: `auth/service/AuthenticationService.java` — フィールド型 + 7 箇所の呼び出し変更

**テスト影響**: `AuthenticationServiceTest` の `@Mock EventPublishingService` → `@Mock EventPublisher`。`verify(eventPublishingService).publishUserRegistered(any())` → `verify(eventPublisher).publish(any())` に変更（7 箇所全て）。ドメイン特化メソッド名がなくなるため、テストで `ArgumentCaptor<DomainEvent<?>>` を使用して `eventType` 文字列を検証するパターンを推奨（§5.3 参照）。

#### パターン C サービス（ドメイン特化 + プリミティブ引数 — point-service, coupon-service）

**現状**: DomainEvent を使わず、プリミティブ引数を直接渡す独自 API。

```java
// point-service の現行
eventPublishingService.publishPointsAwarded(userId, points, transactionId);

// coupon-service の現行
eventPublishingService.publishCouponCreated(couponId, campaignId, code);
```

**移行方法**: `DomainEvent.create()` でラップする形に変更。ペイロードレコードを各サービスに作成する。

```java
// point-service: After
eventPublisher.publish(DomainEvent.create("points.awarded", "point-service",
    new PointsAwardedPayload(userId, points, transactionId)));

// coupon-service: After
eventPublisher.publish(DomainEvent.create("coupon.created", "coupon-service",
    new CouponCreatedPayload(couponId, campaignId, code)));
```

**新規ペイロードレコード（point-service に作成）**:

```java
// point-service/src/.../point/event/PointEventPayloads.java
public final class PointEventPayloads {
    private PointEventPayloads() {}

    public record PointsAwardedPayload(String userId, int points, String transactionId) {}
    public record PointsRedeemedPayload(String userId, int points, String redemptionType) {}
    public record PointsTransferredPayload(String fromUserId, String toUserId, int amount) {}
    public record TierUpgradedPayload(String userId, String oldTier, String newTier) {}
}
```

**新規ペイロードレコード（coupon-service に作成）**:

```java
// coupon-service/src/.../coupon/event/CouponEventPayloads.java
public final class CouponEventPayloads {
    private CouponEventPayloads() {}

    public record CouponCreatedPayload(String couponId, String campaignId, String code) {}
    public record CouponValidatedPayload(String couponId, String userId, boolean isValid) {}
    public record CouponRedeemedPayload(String couponId, String userId, String orderId, String discountApplied) {}
    public record CampaignActivatedPayload(String campaignId, String name) {}
    public record CampaignCompletedPayload(String campaignId, int totalCoupons, int totalUsage, String reason) {}
}
```

**変更ファイル**:

| サービス | 削除 | 新規 | 変更 |
|---------|------|------|------|
| point-service | `EventPublishingService.java` | `event/PointEventPayloads.java` | `PointService.java` (4箇所), `PointServiceTest.java` (Mock差替 + verify修正) |
| coupon-service | `EventPublishingService.java` | `event/CouponEventPayloads.java` | `CouponService.java` (3箇所), `CampaignService.java` (1箇所), `CouponServiceTest.java` (Mock差替 + verify修正), `CampaignServiceTest.java` (Mock差替 + verify修正) |

### 3.3 イベントタイプ命名規約

全サービスで統一するイベントタイプ命名規約:

```
<bounded-context>.<aggregate>.<action>
```

| 現行 eventType | 統一後 eventType | サービス |
|---------------|-----------------|---------|
| `USER_REGISTERED` | `auth.user.registered` | authentication |
| `USER_DELETED` | `auth.user.deleted` | authentication |
| `USER_AUTHENTICATED` | `auth.user.authenticated` | authentication |
| `USER_LOGGED_OUT` | `auth.user.logged_out` | authentication |
| `LOGIN_FAILED` | `auth.user.login_failed` | authentication |
| `ACCOUNT_LOCKED` | `auth.user.account_locked` | authentication |
| `PASSWORD_CHANGED` | `auth.user.password_changed` | authentication |
| `user.created` | `usermgmt.user.created` | user-management |
| `user.updated` | `usermgmt.user.updated` | user-management |
| `user.deleted` | `usermgmt.user.deleted` | user-management |
| `user.verified` | `usermgmt.user.verified` | user-management |
| `user.role_changed` | `usermgmt.user.role_changed` | user-management |
| `user.password_changed` | `usermgmt.user.password_changed` | user-management |
| `OrderCreated` | `sales.order.created` | sales-management |
| `OrderStatusUpdated` | `sales.order.status_updated` | sales-management |
| `OrderCancelled` | `sales.order.cancelled` | sales-management |
| `ShipmentCreated` | `sales.shipment.created` | sales-management |
| `ReturnRequested` | `sales.return.requested` | sales-management |
| `ReturnProcessed` | `sales.return.processed` | sales-management |
| `ProductCreated` | `inventory.product.created` | inventory-management |
| `InventoryReserved` | `inventory.stock.reserved` | inventory-management |
| `InventoryUpdated` | `inventory.stock.updated` | inventory-management |
| `InventoryLow` | `inventory.stock.low` | inventory-management |
| `InventoryOutOfStock` | `inventory.stock.out_of_stock` | inventory-management |
| `PriceUpdated` | `inventory.product.price_updated` | inventory-management |
| (新規) `points.awarded` | `point.transaction.awarded` | point |
| (新規) `points.redeemed` | `point.transaction.redeemed` | point |
| (新規) `points.transferred` | `point.transaction.transferred` | point |
| (新規) `tier.upgraded` | `point.tier.upgraded` | point |
| (新規) `coupon.created` | `coupon.coupon.created` | coupon |
| (新規) `coupon.validated` | `coupon.coupon.validated` | coupon |
| (新規) `coupon.redeemed` | `coupon.coupon.redeemed` | coupon |
| (新規) `campaign.activated` | `coupon.campaign.activated` | coupon |
| (新規) `campaign.completed` | `coupon.campaign.completed` | coupon |

> **注意**: Phase 1 ではイベントタイプの命名変更は任意（後方互換性を優先）。Phase 2 の Kafka 統合時に統一命名規約を適用する。

---

## 4. Phase 2 設計（Kafka 統合 — 将来実装）

### 4.1 Spring Cloud Stream 実装

```java
package com.example.skishop.common.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

/**
 * Spring Cloud Stream (Kafka) によるイベント発行実装。
 * Phase 2 で各サービスに導入する。
 */
public class SpringCloudStreamEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(
            SpringCloudStreamEventPublisher.class);

    private final StreamBridge streamBridge;
    private final ObjectMapper objectMapper;
    private final String defaultBindingName;

    public SpringCloudStreamEventPublisher(StreamBridge streamBridge,
                                            ObjectMapper objectMapper,
                                            String defaultBindingName) {
        this.streamBridge = streamBridge;
        this.objectMapper = objectMapper;
        this.defaultBindingName = defaultBindingName;
    }

    @Override
    public <T> void publish(DomainEvent<T> event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            Message<String> message = MessageBuilder.withPayload(json)
                    .setHeader("eventType", event.eventType())
                    .setHeader("eventId", event.eventId())
                    .setHeader("correlationId", event.correlationId())
                    .setHeader("producer", event.producer())
                    .setHeader("timestamp", event.timestamp().toString())
                    .build();

            boolean sent = streamBridge.send(defaultBindingName, message);
            if (sent) {
                log.info("Event sent to Kafka: type={}, eventId={}, correlationId={}",
                        event.eventType(), event.eventId(), event.correlationId());
            } else {
                throw new EventPublishException(
                        "Failed to send event: eventId=" + event.eventId());
            }
        } catch (JsonProcessingException e) {
            throw new EventPublishException(
                    "Failed to serialize event: eventId=" + event.eventId(), e);
        }
    }
}
```

> **注意**: 上記の実装では `objectMapper.writeValueAsString(event)` で手動 JSON 変換を行っているが、
> Spring Cloud Stream は組込みの message converter を内蔵しており、`StreamBridge.send(bindingName, event)` で
> `DomainEvent` オブジェクトを直接渡すことも可能。Phase 2 設計時に組込み converter の利用を検討し、
> 手動変換が本当に必要か（カスタムヘッダー付与等の要件があるか）を評価すること。

### 4.2 Kafka トピック構成

```properties
# application.properties (Phase 2 共通テンプレート)
spring.cloud.stream.bindings.domainEvents-out-0.destination=skishop.${spring.application.name}.events
spring.cloud.stream.bindings.domainEvents-out-0.content-type=application/json
spring.cloud.stream.kafka.binder.brokers=${KAFKA_BROKERS:localhost:9092}
spring.cloud.stream.kafka.binder.auto-create-topics=true
spring.cloud.stream.kafka.binder.replicationFactor=3
spring.cloud.stream.kafka.bindings.domainEvents-out-0.producer.acks=all
spring.cloud.stream.kafka.bindings.domainEvents-out-0.producer.retries=3
spring.cloud.stream.kafka.bindings.domainEvents-out-0.producer.configuration.enable.idempotence=true
```

### 4.3 Phase 3 Outbox パターン（将来拡張設計）

```
┌──────────────────────────┐
│   Service (トランザクション内) │
│                            │
│  1. ビジネスロジック実行    │
│  2. outbox テーブルに INSERT│
│  3. COMMIT                 │
└────────────┬───────────────┘
             │
             ▼
┌──────────────────────────┐
│   Outbox Relay (CDC)      │
│   Debezium / Polling      │
│                            │
│  outbox テーブルを監視     │
│  → Kafka に publish       │
│  → outbox レコード削除    │
└──────────────────────────┘
```

```sql
-- Phase 3 用 outbox テーブル DDL
CREATE TABLE event_outbox (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id        VARCHAR(50) NOT NULL UNIQUE,
    event_type      VARCHAR(100) NOT NULL,
    producer        VARCHAR(50) NOT NULL,
    payload         JSONB NOT NULL,
    correlation_id  VARCHAR(50),
    version         INT NOT NULL DEFAULT 1,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMP WITH TIME ZONE,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING'
);

CREATE INDEX idx_outbox_status ON event_outbox(status) WHERE status = 'PENDING';
```

---

## 5. 実装における注意点

### 5.1 Critical な注意事項

| # | カテゴリ | 注意点 | リスク | 対策 |
|---|---------|--------|--------|------|
| 1 | **トランザクション境界** | `EventPublisher.publish()` は `@Transactional` メソッド内で呼ばれる。ログのみなら問題ないが、Kafka 送信は DB トランザクションの外で実行される | DB コミット成功 + Kafka 送信失敗 → データ不整合 | Phase 1 はログのみ（リスクなし）。Phase 2 では Outbox パターン必須。Phase 2 の Kafka 直接送信は「少なくとも 1 回配信」として許容できるイベントのみ |
| 2 | **イベント順序保証** | Kafka パーティション内のみ順序保証。異なるパーティションでは順序不定 | 同一ユーザーのイベント順序逆転 | パーティションキーを `userId` または `aggregateId` に設定。Phase 2 で messageKey ヘッダーを追加 |
| 3 | **テスト互換性** | 既存テストは `@Mock EventPublishingService` + `verify(...).publishXxx(any())` パターン | テスト全壊 | 段階的移行：インターフェース変更 → テスト修正 → 旧クラス削除の順 |
| 4 | **Component Scan** | common-lib の `EventPublisherAutoConfiguration` が各サービスの Spring Boot Application で自動読込されること | Bean 未登録で NoSuchBeanDefinitionException | `@Configuration` + `@ConditionalOnMissingBean` パターン。`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` にクラス名登録 |
| 5 | **循環依存回避** | 一部サービスの EventPublishingService が `@Service` で自身もスキャン対象 | 旧 `@Service` EventPublishingService と新 `@Bean` LoggingEventPublisher の競合 | 旧ファイル削除を先行。削除前に一時的に `@Primary` 指定で回避も可能 |

### 5.2 High な注意事項

| # | カテゴリ | 注意点 |
|---|---------|--------|
| 6 | **Jackson シリアライズ** | `DomainEvent<T>` のペイロード型 `T` が `Object` にワイルドカードされるとデシリアライズ時に型情報が失われる。Phase 2 で `@JsonTypeInfo` を付与するか、型ヒントヘッダーを使用する |
| 7 | **ペイロード不変性** | イベントペイロードは不変であるべき。record クラスの使用を必須とする。ミュータブルな POJO をペイロードにしない |
| 8 | **イベントスキーマ進化** | ペイロードにフィールドを追加する場合、コンシューマが未知フィールドを無視できること（`@JsonIgnoreProperties(ignoreUnknown = true)`）。フィールド削除・型変更は破壊的変更として禁止 |
| 9 | **べき等性** | イベントコンシューマ側は `eventId` で重複排除する設計を前提とする。同一 `eventId` のイベントが複数回配信される可能性がある |
| 10 | **CorrelationId 伝播** | API Gateway → 各サービスへの Correlation ID 伝播が CorrelationIdFilter で実装済み。イベント発行時に SecurityContext / MDC から CorrelationId を取得して DomainEvent に設定する設計を推奨 |

### 5.3 テスト戦略

#### パターン A/B: 汎用 `publish` → `publish` 移行（verify パターンの単純差替）

```java
// パターン A サービス（user-mgmt, sales, payment-cart, inventory）のテスト例
// Before: verify(eventPublishingService).publish(any());
// After:  verify(eventPublisher).publish(any());
// → メソッドシグネチャが同一のため、Mock 型のみ変更すれば互換

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private EventPublisher eventPublisher;  // インターフェースをモック

    @Test
    void createUser_shouldPublishEvent() {
        // ... setup ...
        userService.createUser(request);

        // DomainEvent の内容を検証
        ArgumentCaptor<DomainEvent<?>> captor =
                ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publish(captor.capture());

        DomainEvent<?> event = captor.getValue();
        assertThat(event.eventType()).isEqualTo("usermgmt.user.created");
        assertThat(event.producer()).isEqualTo("user-management-service");
    }
}
```

#### パターン C: プリミティブ引数 → DomainEvent ラップ移行（verify パターンの大幅変更）

```java
// パターン C サービス（point-service, coupon-service）のテスト例
// ⚠️ 注意: 旧テストはプリミティブ引数を直接検証していたため、
//    verify の書換えだけでなく ArgumentCaptor でペイロード内容を検証する必要がある

// Before（coupon-service の例）:
//   verify(eventPublishingService).publishCouponCreated(any(), any(), eq("SPRING10"));
// After:
@Test
void createCoupon_shouldPublishEvent() {
    // ... setup ...
    couponService.createCoupon(request);

    ArgumentCaptor<DomainEvent<?>> captor =
            ArgumentCaptor.forClass(DomainEvent.class);
    verify(eventPublisher).publish(captor.capture());

    DomainEvent<?> event = captor.getValue();
    assertThat(event.eventType()).isEqualTo("coupon.created");
    assertThat(event.producer()).isEqualTo("coupon-service");

    // ペイロードの内容を検証（型安全性の補完）
    var payload = (CouponEventPayloads.CouponCreatedPayload) event.payload();
    assertThat(payload.code()).isEqualTo("SPRING10");
}

// Before（point-service の例）:
//   verify(eventPublishingService).publishPointsAwarded(eq(userId.toString()), eq(100), any());
// After:
@Test
void awardPoints_shouldPublishEvent() {
    // ... setup ...
    pointService.awardPoints(request);

    ArgumentCaptor<DomainEvent<?>> captor =
            ArgumentCaptor.forClass(DomainEvent.class);
    verify(eventPublisher).publish(captor.capture());

    DomainEvent<?> event = captor.getValue();
    assertThat(event.eventType()).isEqualTo("points.awarded");

    var payload = (PointEventPayloads.PointsAwardedPayload) event.payload();
    assertThat(payload.userId()).isEqualTo(userId.toString());
    assertThat(payload.points()).isEqualTo(100);
}
```

---

## 6. 実装計画

### 6.1 Phase 1: common-lib 抽象化 + 全サービス統一（今回実施）

#### Step 1: common-lib にインターフェースと実装を追加

| # | ファイル | 操作 | 内容 |
|---|---------|------|------|
| 1-1 | `common-lib/.../event/EventPublisher.java` | **新規** | インターフェース定義 |
| 1-2 | `common-lib/.../event/EventPublishException.java` | **新規** | 例外クラス |
| 1-3 | `common-lib/.../event/LoggingEventPublisher.java` | **新規** | ログ出力実装 |
| 1-4 | `common-lib/.../event/EventPublisherAutoConfiguration.java` | **新規** | 自動構成 |
| 1-5 | `common-lib/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | **新規** | 自動構成登録 |

#### Step 2: パターン A サービスの移行（4 サービス）

| # | サービス | 操作 | 対象ファイル |
|---|---------|------|------------|
| 2-1 | user-management | 削除 | `service/EventPublishingService.java` |
| 2-2 | user-management | 変更 | `service/UserService.java` — `EventPublisher` に差替 |
| 2-3 | user-management | 変更 | `test/.../UserServiceTest.java` — Mock 差替 |
| 2-4 | sales-management | 削除 | `service/EventPublishingService.java` |
| 2-5 | sales-management | 変更 | `service/OrderService.java` — `EventPublisher` に差替 |
| 2-6 | sales-management | 変更 | `test/.../OrderServiceTest.java` — Mock 差替 |
| 2-7 | payment-cart | 削除 | `service/EventPublishingService.java` |
| 2-8 | payment-cart | 変更 | `service/PaymentService.java` (3箇所), `service/CartService.java` (4箇所) — `EventPublisher` に差替 |
| 2-9 | payment-cart | 変更 | `test/.../PaymentServiceTest.java`, `test/.../CartServiceTest.java` — Mock 差替 |
| 2-10 | inventory-management | 削除 | `service/EventPublishingService.java` |
| 2-11 | inventory-management | 変更 | `service/ProductService.java` (8箇所), `service/CategoryService.java` — `EventPublisher` に差替 |
| 2-12 | inventory-management | 変更 | `test/.../ProductServiceTest.java`, `test/.../CategoryServiceTest.java` — Mock 差替 |

#### Step 3: パターン B サービスの移行（authentication-service）

| # | 操作 | 対象ファイル |
|---|------|------------|
| 3-1 | 削除 | `auth/service/EventPublishingService.java` |
| 3-2 | 変更 | `auth/service/AuthenticationService.java` — 7 箇所の呼び出し変更 |
| 3-3 | 変更 | `test/.../AuthenticationServiceTest.java` — Mock 差替 + verify 修正 |

#### Step 4: パターン C サービスの移行（point-service, coupon-service）

| # | サービス | 操作 | 対象ファイル |
|---|---------|------|------------|
| 4-1 | point-service | **新規** | `event/PointEventPayloads.java` — 4 ペイロードレコード |
| 4-2 | point-service | 削除 | `service/EventPublishingService.java` |
| 4-3 | point-service | 変更 | `service/PointService.java` — 4 箇所の DomainEvent ラップ |
| 4-4 | coupon-service | **新規** | `event/CouponEventPayloads.java` — 5 ペイロードレコード |
| 4-5 | coupon-service | 削除 | `service/EventPublishingService.java` |
| 4-6 | coupon-service | 変更 | `service/CouponService.java` — 3 箇所変更 |
| 4-7 | coupon-service | 変更 | `service/CampaignService.java` — 1 箇所変更 |
| 4-8 | coupon-service | 変更 | `test/.../CouponServiceTest.java` — Mock 差替 + ArgumentCaptor でペイロード検証に書換え |
| 4-9 | coupon-service | 変更 | `test/.../CampaignServiceTest.java` — Mock 差替 + verify 修正 |
| 4-10 | point-service | 変更 | `test/.../PointServiceTest.java` — Mock 差替 + ArgumentCaptor でペイロード検証に書換え |

#### Step 5: 共通テスト追加

| # | 対象 | 内容 |
|---|------|------|
| 5-1 | common-lib | `LoggingEventPublisherTest.java` — publish が例外を投げずログ出力すること |
| 5-2 | common-lib | `EventPublisherAutoConfigurationTest.java` — ConditionalOnMissingBean の動作確認 |

#### Step 6: ビルド検証

```bash
cd /private/tmp/spring-ai-sample
mvn clean test -T 4
# 全テスト Pass を確認
```

### 6.2 Phase 2: Kafka 統合（将来 — 別スプリント）

| Step | 内容 | 条件 |
|------|------|------|
| P2-1 | Kafka ブローカーのプロビジョニング | インフラチームの Kafka クラスタ構築完了 |
| P2-2 | `SpringCloudStreamEventPublisher` を common-lib に追加 | — |
| P2-3 | 各サービスの `application.properties` に Kafka binding 設定追加 | — |
| P2-4 | 各サービスで `@Bean EventPublisher` を `SpringCloudStreamEventPublisher` に変更 | `@ConditionalOnProperty("spring.cloud.stream.kafka.binder.brokers")` で切替 |
| P2-5 | イベントコンシューマの実装（各サービスの `@Bean Consumer<Message<String>>` ） | — |
| P2-6 | 統合テスト（Testcontainers + Kafka） | — |
| P2-7 | イベントタイプ命名規約の統一適用 | — |

### 6.3 Phase 3: Transactional Outbox（将来 — エンタープライズ要件確定後）

| Step | 内容 |
|------|------|
| P3-1 | `event_outbox` テーブル DDL を Flyway マイグレーションに追加 |
| P3-2 | `OutboxEventPublisher` 実装（トランザクション内で outbox テーブルに INSERT） |
| P3-3 | Outbox Relay プロセス（Debezium CDC or Polling） |
| P3-4 | Exactly-Once Delivery の検証 |

---

## 7. リスク分析

| # | リスク | 確率 | 影響 | 緩和策 |
|---|--------|------|------|--------|
| 1 | テスト壊れ | 高 | Medium | Mock 差替は機械的。CI で即検出可能 |
| 2 | AutoConfiguration 未読込 | 中 | High | `AutoConfiguration.imports` ファイル + 動確テスト |
| 3 | authentication-service の独自 JwtAuthenticationFilter と Bean 競合 | 低 | Medium | authentication-service は独自 JwtAuthenticationFilter を @Component で持つが、EventPublisher とは無関係 |
| 4 | CouponServiceTest の verify パターン不一致 | 高 | Low | `verify(eventPublisher).publishCouponCreated(...)` → `verify(eventPublisher).publish(any())` に修正必須 |
| 5 | ProductService の複数 Payload レコード（inner records → 移動対象） | 中 | Low | ProductService 内の inner record は既存のまま維持。DomainEvent ペイロードとして利用 |

---

## 8. ADR（Architecture Decision Record）

### ADR-001: EventPublisher インターフェース抽象化

| 項目 | 内容 |
|------|------|
| **ステータス** | 提案 (Proposed) |
| **コンテキスト** | 7 サービスに重複した EventPublishingService が存在し、全てログ出力のみのスタブ。DRY 違反 (A-H-03) と Kafka 未統合 (A-H-01) が指摘されている |
| **決定** | common-lib に `EventPublisher` インターフェースと `LoggingEventPublisher` デフォルト実装を配置。`@ConditionalOnMissingBean` で将来の差替を可能にする。Phase 1 ではログのみ、Phase 2 で Kafka 統合 |
| **選択肢** | (A) 各サービスに個別スタブ維持 (B) **共通インターフェース + AutoConfiguration** (C) Spring Cloud Stream 即時統合 |
| **選択理由** | (A) は DRY 違反継続。(C) は Kafka インフラ未整備で不可。(B) は最小変更で DRY 解消 + 将来の拡張性確保 |
| **得たもの** | DRY 原則遵守、インターフェースによる実装差替可能性、テスタビリティ向上 |
| **犠牲にしたもの** | 軽微な抽象化コスト、ドメイン特化メソッドの型安全性（汎用 `publish(DomainEvent)` への統一で引数型チェックが緩くなる） |
| **緩和策** | ペイロードレコードの型で実質的な型安全性を維持。テストで eventType 文字列を検証 |

### ADR-002: Kafka スタブ（ログ出力のみ）の 1.0 リリース許容

| 項目 | 内容 |
|------|------|
| **ステータス** | 提案 (Proposed) — ⚠️ 要人間アーキテクト判断 |
| **コンテキスト** | 全サービスのイベント発行は Phase 1 時点でログ出力のみ。Kafka ブローカーは未構築。サービス間のイベント駆動連携は機能しない |
| **決定** | 1.0 リリースではログ出力スタブで出荷し、Post-release Phase 2 で Kafka 統合を行う |
| **リスク** | サービス間のイベント整合性がない（例: auth-service の USER_REGISTERED が user-management-service に伝播しない）。手動同期または API 呼び出しで補完が必要 |
| **受容条件** | すべてのクリティカルなサービス間連携が同期 REST API で補完されていること。イベントはログに記録されており、手動リプレイが可能であること |

---

## 9. 検証チェックリスト

Phase 1 実装完了後に全て確認すること:

- [ ] `mvn clean test -T 4` が全テスト Pass（テスト数が減少していないこと）
- [ ] 全 7 サービスの `service/EventPublishingService.java` が削除されていること
- [ ] common-lib に `EventPublisher`, `LoggingEventPublisher`, `EventPublisherAutoConfiguration` が存在すること
- [ ] `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` が存在すること
- [ ] 全サービスの Service クラスが `EventPublisher` インターフェースを注入していること
- [ ] point-service に `event/PointEventPayloads.java` が存在し、全イベントが `DomainEvent` でラップされていること
- [ ] coupon-service に `event/CouponEventPayloads.java` が存在し、全イベントが `DomainEvent` でラップされていること
- [ ] authentication-service の 7 イベント発行が `eventPublisher.publish(DomainEvent.create(...))` 形式に統一されていること
- [ ] `LoggingEventPublisherTest` が存在し Pass すること
- [ ] 各サービスのイベント発行呼出数がテストの verify 数と一致すること（テスト漏れ検出）
- [ ] パターン C サービス（point, coupon）のテストで `ArgumentCaptor` によるペイロード検証が実装されていること
- [ ] 各サービスを個別起動してヘルスチェックが UP であること（`LoggingEventPublisher` が自動注入されること）
