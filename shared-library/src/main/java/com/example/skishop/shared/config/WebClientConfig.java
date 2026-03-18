package com.example.skishop.shared.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * WebClient 共通設定クラス。
 * マイクロサービス間通信で使用する WebClient の共通設定を提供する。
 */
@Configuration
public class WebClientConfig {

    private static final Logger log = LoggerFactory.getLogger(WebClientConfig.class);

    @Value("${webclient.connect-timeout-seconds:10}")
    private int connectTimeoutSeconds;

    @Value("${webclient.response-timeout-seconds:30}")
    private int responseTimeoutSeconds;

    /**
     * 共通設定を持つ WebClient.Builder を生成する。
     * 各サービスはこの Builder をベースにサービス固有の URL を設定する。
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutSeconds * 1000)
                .responseTimeout(Duration.ofSeconds(responseTimeoutSeconds))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(responseTimeoutSeconds, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(responseTimeoutSeconds, TimeUnit.SECONDS)));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .filter(requestLoggingFilter())
                .filter(responseLoggingFilter());
    }

    /**
     * デフォルト設定の WebClient インスタンスを生成する。
     */
    @Bean
    public WebClient webClient(WebClient.Builder webClientBuilder) {
        return webClientBuilder.build();
    }

    /**
     * リクエストログフィルター。
     * メソッド、URL をログに記録する。秘密情報は記録しない。
     */
    private ExchangeFilterFunction requestLoggingFilter() {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            log.debug("WebClient リクエスト: {} {}", request.method(), request.url());
            return Mono.just(request);
        });
    }

    /**
     * レスポンスログフィルター。
     * HTTP ステータスコードをログに記録する。
     */
    private ExchangeFilterFunction responseLoggingFilter() {
        return ExchangeFilterFunction.ofResponseProcessor(response -> {
            log.debug("WebClient レスポンス: ステータス={}", response.statusCode());
            return Mono.just(response);
        });
    }
}
