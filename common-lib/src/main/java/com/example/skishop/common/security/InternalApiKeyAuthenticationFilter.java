package com.example.skishop.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 内部サービス間呼び出し用の API キー認証フィルタ。
 *
 * <p>適用範囲:
 * <ul>
 *   <li>既存ドメインサービス（user-management 等）— 常時有効。Multi-Agent からの REST 呼び出しを認証</li>
 *   <li>Multi-Agent Worker — 分散モードのみ（{@code agents.deployment.mode=distributed}）</li>
 * </ul>
 *
 * <p>有効化は呼び出し側（SecurityConfig）で {@code addFilterBefore(filter, JwtAuthenticationFilter.class)} する。
 *
 * <p>ヘッダ:
 * <ul>
 *   <li>{@code X-Internal-Api-Key} — 32 文字以上のランダム文字列。全サービス共通の {@code INTERNAL_API_KEY} と一致する必要あり</li>
 *   <li>{@code X-Caller-Service} — 呼び出し元サービス名（任意。ログ・監査に使用）</li>
 * </ul>
 *
 * <p>認証成功時、{@code ROLE_AGENT} 権限の {@link UsernamePasswordAuthenticationToken} を SecurityContext に設定する。
 */
public class InternalApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(InternalApiKeyAuthenticationFilter.class);
    private static final String HEADER_API_KEY = "X-Internal-Api-Key";
    private static final String HEADER_CALLER  = "X-Caller-Service";

    private final String expectedApiKey;

    public InternalApiKeyAuthenticationFilter(String expectedApiKey) {
        if (expectedApiKey == null || expectedApiKey.isBlank()) {
            throw new IllegalArgumentException("INTERNAL_API_KEY must not be blank");
        }
        this.expectedApiKey = expectedApiKey;
    }

    /**
     * SseEmitter の非同期ディスパッチ時にも API キーフィルタを再実行する。
     */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String apiKey = request.getHeader(HEADER_API_KEY);
        if (apiKey != null && !apiKey.isBlank() && apiKey.equals(expectedApiKey)) {
            String caller = request.getHeader(HEADER_CALLER);
            String principal = (caller != null && !caller.isBlank()) ? caller : "internal-service";
            var auth = new UsernamePasswordAuthenticationToken(
                    principal, null,
                    List.of(new SimpleGrantedAuthority("ROLE_AGENT")));
            SecurityContextHolder.getContext().setAuthentication(auth);
            log.debug("Internal API key authenticated. caller={}", principal);
        }
        chain.doFilter(request, response);
    }
}
