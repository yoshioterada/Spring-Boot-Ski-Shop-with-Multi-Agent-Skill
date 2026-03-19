package com.example.skishop.gateway.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

@TestConfiguration
public class TestSecurityConfig {

    @Bean
    @Primary
    public ReactiveJwtDecoder testReactiveJwtDecoder() {
        return token -> {
            var jwt = Jwt.withTokenValue(token)
                    .header("alg", "RS256")
                    .claim("sub", "test-user")
                    .claim("roles", List.of("ROLE_USER"))
                    .claim("scope", "openid")
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();
            return Mono.just(jwt);
        };
    }

    public static Jwt createAdminJwt() {
        return Jwt.withTokenValue("admin-token")
                .header("alg", "RS256")
                .claim("sub", "admin-user")
                .claim("roles", List.of("ROLE_ADMIN"))
                .claim("scope", "openid")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    public static Jwt createManagerJwt() {
        return Jwt.withTokenValue("manager-token")
                .header("alg", "RS256")
                .claim("sub", "manager-user")
                .claim("roles", List.of("ROLE_MANAGER"))
                .claim("scope", "openid")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    public static Jwt createUserJwt() {
        return Jwt.withTokenValue("user-token")
                .header("alg", "RS256")
                .claim("sub", "regular-user")
                .claim("roles", List.of("ROLE_USER"))
                .claim("scope", "openid")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }
}
