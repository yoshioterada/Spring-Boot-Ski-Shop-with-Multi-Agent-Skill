package com.example.skishop.agent.runtime.config;

import com.example.skishop.common.security.InternalApiKeyAuthenticationFilter;
import com.example.skishop.common.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * モノリス実行時の単一 SecurityFilterChain。
 *
 * <ul>
 *   <li>{@code /actuator/health}, {@code /actuator/info}, {@code /actuator/prometheus} は permitAll</li>
 *   <li>{@code /api/v1/orchestrator/**} はフロント API のため JWT 認証 + USER/ADMIN/MANAGER ロール</li>
 *   <li>{@code /api/v1/agents/**} は管理用途のため JWT 認証 + AGENT_ADMIN/ADMIN ロール</li>
 *   <li>その他は認証必須</li>
 * </ul>
 *
 * <p>モノリスでは Worker 間呼び出しが in-JVM のため {@code InternalApiKeyAuthenticationFilter} は登録しない。</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class MonolithSecurityConfig {

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(@Value("${jwt.secret}") String jwtSecret) {
        return new JwtAuthenticationFilter(jwtSecret);
    }

    @Bean
    public InternalApiKeyAuthenticationFilter internalApiKeyAuthenticationFilter(
            @Value("${services.internal-api-key:${internal.api-key:}}") String apiKey) {
        return new InternalApiKeyAuthenticationFilter(apiKey);
    }

    @Bean
    public SecurityFilterChain monolithSecurityFilterChain(HttpSecurity http,
                                                           JwtAuthenticationFilter jwtFilter,
                                                           InternalApiKeyAuthenticationFilter internalFilter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/v1/orchestrator/**").hasAnyRole("USER", "ADMIN", "MANAGER")
                        .requestMatchers("/api/v1/agents/**").hasAnyRole("AGENT_ADMIN", "ADMIN", "AGENT")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(internalFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
