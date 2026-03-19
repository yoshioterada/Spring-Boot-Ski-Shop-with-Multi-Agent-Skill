package com.example.skishop.gateway.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    @DisplayName("X-Correlation-Idが未設定の場合、UUIDが自動生成される")
    void should_generateCorrelationId_when_headerNotPresent() {
        // Arrange
        var request = MockServerHttpRequest.method(HttpMethod.GET, "/api/v1/products").build();
        var exchange = MockServerWebExchange.from(request);
        var chain = mock(GatewayFilterChain.class);
        when(chain.filter(org.mockito.ArgumentMatchers.any())).thenReturn(Mono.empty());

        // Act
        filter.filter(exchange, chain).block();

        // Assert
        var correlationId = exchange.getResponse().getHeaders().getFirst("X-Correlation-Id");
        assertThat(correlationId).isNotNull().isNotBlank();
        assertThat(correlationId).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    @DisplayName("X-Correlation-Idが設定済みの場合、既存値が保持される")
    void should_preserveCorrelationId_when_headerPresent() {
        // Arrange
        String existingId = "existing-correlation-id";
        var request = MockServerHttpRequest.method(HttpMethod.GET, "/api/v1/products")
                .header("X-Correlation-Id", existingId)
                .build();
        var exchange = MockServerWebExchange.from(request);
        var chain = mock(GatewayFilterChain.class);
        when(chain.filter(org.mockito.ArgumentMatchers.any())).thenReturn(Mono.empty());

        // Act
        filter.filter(exchange, chain).block();

        // Assert
        var correlationId = exchange.getResponse().getHeaders().getFirst("X-Correlation-Id");
        assertThat(correlationId).isEqualTo(existingId);
    }

    @Test
    @DisplayName("フィルターの順序が最高優先度であること")
    void should_haveHighestPrecedence_when_getOrderCalled() {
        assertThat(filter.getOrder()).isEqualTo(org.springframework.core.Ordered.HIGHEST_PRECEDENCE);
    }
}
