package com.example.skishop.ai.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.util.concurrent.TimeUnit;

/**
 * AI Analyzer の WebClient Bean 群（spec § 2.2 B1）.
 * <p>
 * 各サービス向け WebClient を独立して定義し、internal API key を Authorization
 * ヘッダに付与する。タイムアウト・接続プールサイズは spec § 10 に準拠.
 */
@Configuration
@EnableConfigurationProperties(AiAnalyzerProperties.class)
public class AiAnalyzerWebClientConfig {

    private static final Logger log = LoggerFactory.getLogger(AiAnalyzerWebClientConfig.class);

    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";
    private static final int CONNECT_TIMEOUT_MS = 3_000;
    private static final int READ_TIMEOUT_SEC = 8;
    private static final int WRITE_TIMEOUT_SEC = 5;

    private final String internalApiKey;
    private final String salesUrl;
    private final String userUrl;
    private final String inventoryUrl;
    private final String couponUrl;
    private final String weatherUrl;

    public AiAnalyzerWebClientConfig(
            @Value("${internal.api-key}") String internalApiKey,
            @Value("${service.sales.url}") String salesUrl,
            @Value("${service.user.url}") String userUrl,
            @Value("${service.inventory.url}") String inventoryUrl,
            @Value("${service.coupon.url}") String couponUrl,
            @Value("${service.weather.url}") String weatherUrl) {
        this.internalApiKey = internalApiKey;
        this.salesUrl = salesUrl;
        this.userUrl = userUrl;
        this.inventoryUrl = inventoryUrl;
        this.couponUrl = couponUrl;
        this.weatherUrl = weatherUrl;
        log.info("AiAnalyzerWebClientConfig initialized: sales={}, user={}, inventory={}, coupon={}, weather={}",
                salesUrl, userUrl, inventoryUrl, couponUrl, weatherUrl);
    }

    @Bean
    public WebClient salesWebClient() {
        return buildWebClient(salesUrl, "salesWebClient");
    }

    @Bean
    public WebClient userWebClient() {
        return buildWebClient(userUrl, "userWebClient");
    }

    @Bean
    public WebClient inventoryWebClient() {
        return buildWebClient(inventoryUrl, "inventoryWebClient");
    }

    @Bean
    public WebClient couponWebClient() {
        return buildWebClient(couponUrl, "couponWebClient");
    }

    @Bean
    public WebClient weatherWebClient() {
        return buildWebClient(weatherUrl, "weatherWebClient");
    }

    private WebClient buildWebClient(String baseUrl, String name) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MS)
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(READ_TIMEOUT_SEC, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(WRITE_TIMEOUT_SEC, TimeUnit.SECONDS)));

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(INTERNAL_API_KEY_HEADER, internalApiKey)
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .defaultHeader(HttpHeaders.USER_AGENT, "ai-support-service/" + name)
                .build();
    }
}
