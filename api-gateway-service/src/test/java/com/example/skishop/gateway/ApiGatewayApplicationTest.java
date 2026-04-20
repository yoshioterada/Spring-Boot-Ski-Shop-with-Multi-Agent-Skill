package com.example.skishop.gateway;

import com.example.skishop.gateway.config.TestSecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class ApiGatewayApplicationTest {

    @Autowired
    private RouteLocator routeLocator;

    @Autowired
    private WebTestClient webTestClient;

    @Test
    @DisplayName("アプリケーションコンテキストが正常にロードされる")
    void contextLoads() {
        assertThat(routeLocator).isNotNull();
    }

    @Test
    @DisplayName("全ルートが設定されている")
    void should_haveAllRoutes_when_applicationStarts() {
        // Act
        List<Route> routes = routeLocator.getRoutes().collectList().block();

        // Assert
        assertThat(routes).isNotNull();
        assertThat(routes).isNotEmpty();

        var routeIds = routes.stream().map(Route::getId).toList();
        assertThat(routeIds).contains(
                "auth-service",
                "user-management-service",
                "inventory-management-service",
                "sales-management-service",
                "payment-cart-service",
                "point-service",
                "coupon-service",
                "ai-support-chat",
                "ai-support-recommendations",
                "ai-support-search",
                "ai-support-analytics",
                "ai-support-models",
                "ai-analyzer"
        );
    }

    @Nested
    @DisplayName("公開エンドポイントのセキュリティテスト")
    class PublicEndpointSecurityTest {

        @Test
        @DisplayName("ヘルスチェックは認証なしでアクセス可能")
        void should_allowAccess_when_healthEndpointWithoutAuth() {
            webTestClient.get().uri("/actuator/health")
                    .exchange()
                    .expectStatus().isOk();
        }

        @Test
        @DisplayName("認証エンドポイントは認証なしでアクセス可能")
        void should_allowAccess_when_authEndpointWithoutAuth() {
            // auth serviceに接続できないので502が返るが、401/403ではない (= 認証チェックは通過)
            webTestClient.get().uri("/api/v1/auth/login")
                    .exchange()
                    .expectStatus().value(status ->
                            assertThat(status).isNotIn(401, 403));
        }

        @Test
        @DisplayName("商品エンドポイントは認証なしでアクセス可能")
        void should_allowAccess_when_productsEndpointWithoutAuth() {
            webTestClient.get().uri("/api/v1/products")
                    .exchange()
                    .expectStatus().value(status ->
                            assertThat(status).isNotIn(401, 403));
        }

        @Test
        @DisplayName("レコメンドエンドポイントは認証なしでアクセス可能")
        void should_allowAccess_when_recommendationsEndpointWithoutAuth() {
            webTestClient.get().uri("/api/v1/recommendations")
                    .exchange()
                    .expectStatus().value(status ->
                            assertThat(status).isNotIn(401, 403));
        }

        @Test
        @DisplayName("検索エンドポイントは認証なしでアクセス可能")
        void should_allowAccess_when_searchEndpointWithoutAuth() {
            webTestClient.get().uri("/api/v1/search")
                    .exchange()
                    .expectStatus().value(status ->
                            assertThat(status).isNotIn(401, 403));
        }

        @Test
        @DisplayName("クーポンGETエンドポイントは認証なしでアクセス可能")
        void should_allowAccess_when_couponsGetEndpointWithoutAuth() {
            webTestClient.get().uri("/api/v1/coupons")
                    .exchange()
                    .expectStatus().value(status ->
                            assertThat(status).isNotIn(401, 403));
        }

        @Test
        @DisplayName("カテゴリエンドポイントは認証なしでアクセス可能")
        void should_allowAccess_when_categoriesEndpointWithoutAuth() {
            webTestClient.get().uri("/api/v1/categories")
                    .exchange()
                    .expectStatus().value(status ->
                            assertThat(status).isNotIn(401, 403));
        }

        @Test
        @DisplayName("キャンペーンGETエンドポイントは認証なしでアクセス可能")
        void should_allowAccess_when_campaignsGetEndpointWithoutAuth() {
            webTestClient.get().uri("/api/v1/campaigns/active")
                    .exchange()
                    .expectStatus().value(status ->
                            assertThat(status).isNotIn(401, 403));
        }
    }

    @Nested
    @DisplayName("認証必須エンドポイントのセキュリティテスト")
    class AuthenticatedEndpointSecurityTest {

        @Test
        @DisplayName("ユーザーエンドポイントは認証なしで401を返す")
        void should_returnUnauthorized_when_usersEndpointWithoutAuth() {
            webTestClient.get().uri("/api/v1/users/me")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("注文エンドポイントは認証なしで401を返す")
        void should_returnUnauthorized_when_ordersEndpointWithoutAuth() {
            webTestClient.get().uri("/api/v1/orders")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("カートエンドポイントは認証なしで401を返す")
        void should_returnUnauthorized_when_cartEndpointWithoutAuth() {
            webTestClient.get().uri("/api/v1/cart")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("決済エンドポイントは認証なしで401を返す")
        void should_returnUnauthorized_when_paymentsEndpointWithoutAuth() {
            webTestClient.get().uri("/api/v1/payments")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("ポイントエンドポイントは認証なしで401を返す")
        void should_returnUnauthorized_when_pointsEndpointWithoutAuth() {
            webTestClient.get().uri("/api/v1/points")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("チャットエンドポイントは認証なしで401を返す")
        void should_returnUnauthorized_when_chatEndpointWithoutAuth() {
            webTestClient.get().uri("/api/v1/chat/sessions")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }

    @Nested
    @DisplayName("ロールベースアクセス制御テスト")
    class RoleBasedAccessControlTest {

        @Test
        @DisplayName("在庫管理エンドポイントは認証なしで401を返す")
        void should_returnUnauthorized_when_inventoryEndpointWithoutAuth() {
            webTestClient.get().uri("/api/v1/inventory")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("レポートエンドポイントは認証なしで401を返す")
        void should_returnUnauthorized_when_reportsEndpointWithoutAuth() {
            webTestClient.get().uri("/api/v1/reports")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("分析エンドポイントは認証なしで401を返す")
        void should_returnUnauthorized_when_analyticsEndpointWithoutAuth() {
            webTestClient.get().uri("/api/v1/analytics")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("モデル管理エンドポイントは認証なしで401を返す")
        void should_returnUnauthorized_when_modelsEndpointWithoutAuth() {
            webTestClient.get().uri("/api/v1/models")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }

    @Nested
    @DisplayName("フォールバックテスト")
    class FallbackTest {

        @Test
        @DisplayName("フォールバックエンドポイントが503をRFC 7807形式で返す")
        void should_return503_when_fallbackTriggered() {
            webTestClient.get().uri("/fallback/test-service")
                    .exchange()
                    .expectStatus().isEqualTo(503)
                    .expectBody()
                    .jsonPath("$.type").isEqualTo("https://skishop.example.com/errors/service-unavailable")
                    .jsonPath("$.title").isEqualTo("Service Unavailable")
                    .jsonPath("$.status").isEqualTo(503)
                    .jsonPath("$.detail").isNotEmpty()
                    .jsonPath("$.instance").isEqualTo("/fallback/test-service")
                    .jsonPath("$.errorCode").isEqualTo("GW-5002")
                    .jsonPath("$.timestamp").isNotEmpty();
        }
    }
}
