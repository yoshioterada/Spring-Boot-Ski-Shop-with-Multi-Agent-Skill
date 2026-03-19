package com.example.skishop.gateway.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    private static final Logger log = LoggerFactory.getLogger(FallbackController.class);

    @GetMapping("/{service}")
    public Mono<Map<String, Object>> getFallback(
            @PathVariable String service,
            ServerWebExchange exchange) {
        return buildFallbackResponse(service, exchange);
    }

    @PostMapping("/{service}")
    public Mono<Map<String, Object>> postFallback(
            @PathVariable String service,
            ServerWebExchange exchange) {
        return buildFallbackResponse(service, exchange);
    }

    private Mono<Map<String, Object>> buildFallbackResponse(String service, ServerWebExchange exchange) {
        log.warn("サーキットブレーカー発動: service={}, path={}",
                service, exchange.getRequest().getURI().getPath());

        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", URI.create("https://skishop.example.com/errors/service-unavailable").toString());
        body.put("title", "Service Unavailable");
        body.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        body.put("detail", "サービスが一時的に利用できません: " + service);
        body.put("instance", exchange.getRequest().getURI().getPath());
        body.put("errorCode", "GW-5002");
        body.put("timestamp", Instant.now().toString());

        return Mono.just(body);
    }
}
