package com.example.skishop.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();

        // レスポンスがコミットされる前に X-Response-Time を付与するためのフック。
        // beforeCommit に登録することで、ヘッダがまだ書き込み可能な段階で確実に追加される。
        exchange.getResponse().beforeCommit(() -> {
            long duration = System.currentTimeMillis() - startTime;
            try {
                exchange.getResponse().getHeaders().add("X-Response-Time", duration + "ms");
            } catch (UnsupportedOperationException ex) {
                // 既にヘッダが ReadOnly 化されている場合は無視（フィルタ自体の失敗は防ぐ）
                log.debug("Response headers already committed; skip X-Response-Time");
            }
            return Mono.empty();
        });

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            long duration = System.currentTimeMillis() - startTime;
            log.info("{} {} -> {} ({}ms)",
                    exchange.getRequest().getMethod(),
                    exchange.getRequest().getURI().getPath(),
                    exchange.getResponse().getStatusCode(),
                    duration);
        }));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
