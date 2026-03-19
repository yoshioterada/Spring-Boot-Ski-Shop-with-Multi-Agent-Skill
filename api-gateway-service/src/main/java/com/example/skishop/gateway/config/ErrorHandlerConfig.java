package com.example.skishop.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.TimeoutException;

@Configuration
public class ErrorHandlerConfig {

    private static final Logger log = LoggerFactory.getLogger(ErrorHandlerConfig.class);

    @Bean
    @Order(-1)
    public ErrorWebExceptionHandler globalErrorHandler() {
        return (ServerWebExchange exchange, Throwable ex) -> {
            HttpStatus status;
            String code;
            String message;

            if (ex instanceof ResponseStatusException rse) {
                status = HttpStatus.valueOf(rse.getStatusCode().value());
                switch (status) {
                    case NOT_FOUND -> {
                        code = "GW-4004";
                        message = "ルートが見つかりません";
                    }
                    case UNAUTHORIZED -> {
                        code = "GW-4001";
                        message = "認証が必要です";
                    }
                    case FORBIDDEN -> {
                        code = "GW-4002";
                        message = "アクセス権限がありません";
                    }
                    case METHOD_NOT_ALLOWED -> {
                        code = "GW-4005";
                        message = "許可されていないHTTPメソッドです";
                    }
                    case TOO_MANY_REQUESTS -> {
                        code = "GW-4291";
                        message = "リクエスト制限を超過しました";
                    }
                    default -> {
                        code = "GW-5004";
                        message = "内部ゲートウェイエラー";
                    }
                }
            } else if (ex instanceof TimeoutException) {
                status = HttpStatus.GATEWAY_TIMEOUT;
                code = "GW-5001";
                message = "ゲートウェイタイムアウト";
            } else if (ex instanceof ConnectException) {
                status = HttpStatus.BAD_GATEWAY;
                code = "GW-5003";
                message = "バックエンドサービスに接続できません";
            } else {
                status = HttpStatus.INTERNAL_SERVER_ERROR;
                code = "GW-5004";
                message = "内部ゲートウェイエラー";
            }

            log.error("ゲートウェイエラー: code={}, path={}, error={}",
                    code, exchange.getRequest().getURI().getPath(), ex.getMessage(), ex);

            exchange.getResponse().setStatusCode(status);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

            var body = """
                    {"timestamp":"%s","status":%d,"error":"%s","code":"%s","message":"%s","path":"%s"}"""
                    .formatted(
                            Instant.now().toString(),
                            status.value(),
                            status.getReasonPhrase(),
                            code,
                            message,
                            exchange.getRequest().getURI().getPath()
                    );

            DataBuffer buffer = exchange.getResponse().bufferFactory()
                    .wrap(body.getBytes(StandardCharsets.UTF_8));
            return exchange.getResponse().writeWith(Mono.just(buffer));
        };
    }
}
