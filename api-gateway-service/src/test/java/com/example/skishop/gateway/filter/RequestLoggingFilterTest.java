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

class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    @Test
    @DisplayName("X-Response-Timeヘッダーがレスポンスに追加される")
    void should_addResponseTimeHeader_when_requestProcessed() {
        // Arrange
        var request = MockServerHttpRequest.method(HttpMethod.GET, "/api/v1/products").build();
        var exchange = MockServerWebExchange.from(request);
        var chain = mock(GatewayFilterChain.class);
        when(chain.filter(org.mockito.ArgumentMatchers.any())).thenReturn(Mono.empty());

        // Act
        filter.filter(exchange, chain).block();

        // Assert
        var responseTime = exchange.getResponse().getHeaders().getFirst("X-Response-Time");
        assertThat(responseTime).isNotNull().endsWith("ms");
    }

    @Test
    @DisplayName("RequestLoggingFilterの順序がCorrelationIdFilterの直後であること")
    void should_haveCorrectOrder_when_getOrderCalled() {
        assertThat(filter.getOrder()).isEqualTo(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 1);
    }
}
