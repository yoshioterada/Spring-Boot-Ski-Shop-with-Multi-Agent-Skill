package com.example.skishop.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder(@Value("${jwt.secret:}") String jwtSecret) {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException("jwt.secret is required for API Gateway (set JWT_SECRET in .env)");
        }

        var secretKey = new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
        return NimbusReactiveJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS512)
                .build();
    }

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        // 公開エンドポイント（認証不要）
                        .pathMatchers("/actuator/health", "/actuator/info").permitAll()
                        .pathMatchers("/v3/api-docs/**", "/swagger-ui/**", "/webjars/**").permitAll()
                        .pathMatchers("/api/v1/auth/**").permitAll()
                        .pathMatchers("/api/v1/products/**").permitAll()
                        .pathMatchers("/api/v1/categories/**").permitAll()
                        .pathMatchers("/api/v1/recommendations/**").permitAll()
                        .pathMatchers("/api/v1/search/**").permitAll()
                        .pathMatchers("/fallback/**").permitAll()
                        // クーポン/キャンペーン: GET は公開、その他は認証必要
                        .pathMatchers(HttpMethod.GET, "/api/v1/coupons/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/v1/campaigns/**").permitAll()
                        // ADMIN/MANAGER 限定エンドポイント
                        .pathMatchers("/api/v1/inventory/**").hasAnyRole("ADMIN", "MANAGER")
                        .pathMatchers("/api/v1/reports/**").hasAnyRole("ADMIN", "MANAGER")
                        .pathMatchers("/api/v1/analytics/**").hasAnyRole("ADMIN", "MANAGER")
                        .pathMatchers("/api/v1/models/**").hasRole("ADMIN")
                        .pathMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        // Actuator は ADMIN のみ
                        .pathMatchers("/actuator/**").hasRole("ADMIN")
                        // その他は認証必須
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(reactiveJwtAuthenticationConverter())));

        return http.build();
    }

    /**
     * JWT のカスタムクレーム `role`（authentication-service が発行）から
     * Spring Security の `ROLE_xxx` 権限へ変換するコンバーター。
     * これがないと {@code hasRole("ADMIN")} のチェックが通らず 403 となる。
     */
    private Converter<Jwt, Mono<AbstractAuthenticationToken>> reactiveJwtAuthenticationConverter() {
        var scopesConverter = new JwtGrantedAuthoritiesConverter();
        scopesConverter.setAuthorityPrefix("SCOPE_");

        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter((Jwt jwt) -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>(scopesConverter.convert(jwt));
            // role クレーム (単一文字列)
            Object roleClaim = jwt.getClaim("role");
            if (roleClaim instanceof String s && !s.isBlank()) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + s));
            }
            // roles クレーム (配列形式) も念のためサポート
            Object rolesClaim = jwt.getClaim("roles");
            if (rolesClaim instanceof List<?> list) {
                for (Object r : list) {
                    if (r instanceof String s && !s.isBlank()) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + s));
                    }
                }
            }
            return authorities;
        });
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }
}
