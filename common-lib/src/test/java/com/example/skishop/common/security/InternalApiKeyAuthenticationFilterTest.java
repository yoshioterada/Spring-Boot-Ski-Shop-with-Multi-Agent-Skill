package com.example.skishop.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class InternalApiKeyAuthenticationFilterTest {

    private static final String VALID_KEY = "test-internal-api-key-32-chars-min-length-ok";
    private InternalApiKeyAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new InternalApiKeyAuthenticationFilter(VALID_KEY);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("コンストラクタ: 空文字キーで IllegalArgumentException")
    void constructor_blank_throws() {
        assertThatThrownBy(() -> new InternalApiKeyAuthenticationFilter(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InternalApiKeyAuthenticationFilter(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("有効な API キーで ROLE_AGENT が認証される")
    void valid_apiKey_authenticates() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Internal-Api-Key", VALID_KEY);
        req.addHeader("X-Caller-Service", "weather-agent");
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo("weather-agent");
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_AGENT");
        verify(chain).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
    }

    @Test
    @DisplayName("X-Caller-Service 未指定時は 'internal-service' が principal")
    void valid_apiKey_no_caller_uses_default_principal() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Internal-Api-Key", VALID_KEY);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo("internal-service");
    }

    @Test
    @DisplayName("無効な API キーでは認証されず、後続フィルタへ委譲")
    void invalid_apiKey_no_authentication() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Internal-Api-Key", "wrong-key");
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
    }

    @Test
    @DisplayName("API キー未指定では認証されず、後続フィルタへ委譲")
    void no_apiKey_no_authentication() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
    }
}
