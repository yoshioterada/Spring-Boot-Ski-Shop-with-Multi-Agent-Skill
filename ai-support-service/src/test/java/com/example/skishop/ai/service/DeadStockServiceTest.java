package com.example.skishop.ai.service;

import com.example.skishop.ai.config.AiAnalyzerProperties;
import com.example.skishop.ai.dto.DeadStockResponse.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * DeadStockService テスト (P5: severity 判定, 割引率クリップ, 弾力性外出し).
 */
@ExtendWith(MockitoExtension.class)
class DeadStockServiceTest {

    @Mock private WebClient salesWebClient;
    @Mock private WebClient inventoryWebClient;
    @Mock private WebClient couponWebClient;
    @Mock private ChatClient.Builder chatClientBuilder;
    @Mock private ChatClient chatClient;
    @Mock private LlmAuditService auditService;

    private AiAnalyzerProperties props;
    private DeadStockService service;

    @BeforeEach
    void setUp() {
        props = new AiAnalyzerProperties();
        props.getDeadStock().setDiscountPctMax(40);
        props.getDeadStock().setDuplicateBlockDays(30);
        props.getDeadStock().setElasticity(Map.of("default", -1.5, "cat-wear", -2.0));
        when(chatClientBuilder.build()).thenReturn(chatClient);

        var sanitizer = new com.example.skishop.ai.util.PromptSanitizer();
        service = new DeadStockService(salesWebClient, inventoryWebClient, couponWebClient,
                props, chatClientBuilder, auditService, sanitizer);
    }

    // ── Severity 判定テスト (D-F4-07) ──

    @Test
    void severity_critical_whenDosOver180() {
        assertThat(service.judgeSeverity(200, 5)).isEqualTo(Severity.CRITICAL);
    }

    @Test
    void severity_critical_whenNoSales30() {
        assertThat(service.judgeSeverity(50, 0)).isEqualTo(Severity.CRITICAL);
    }

    @Test
    void severity_high_whenDosBetween91And180() {
        assertThat(service.judgeSeverity(120, 5)).isEqualTo(Severity.HIGH);
    }

    @Test
    void severity_medium_whenDosUnder91() {
        assertThat(service.judgeSeverity(60, 10)).isEqualTo(Severity.MEDIUM);
    }

    @Test
    void severity_boundary_180_isCritical() {
        // > 180 is CRITICAL, 180 is not
        assertThat(service.judgeSeverity(180, 5)).isEqualTo(Severity.HIGH);
        assertThat(service.judgeSeverity(181, 5)).isEqualTo(Severity.CRITICAL);
    }

    @Test
    void severity_boundary_90_isHigh() {
        assertThat(service.judgeSeverity(90, 5)).isEqualTo(Severity.MEDIUM);
        assertThat(service.judgeSeverity(91, 5)).isEqualTo(Severity.HIGH);
    }

    @Test
    void severity_onlyThreeValues() {
        // D-F4-07: 3段階固定
        assertThat(Severity.values()).hasSize(3);
    }

    // ── 割引率クリップテスト (D-F4-01) ──

    @Test
    void discountPct_clipsToMax40() {
        // DoS=1000 → 高い値を計算するが 40% でクリップ
        double pct = service.calculateDiscountPct(1000, "default");
        assertThat(pct).isLessThanOrEqualTo(40.0);
    }

    @Test
    void discountPct_clipsToMin0() {
        // DoS=1 → 負の値になるべきだが 0% でクリップ
        double pct = service.calculateDiscountPct(1, "default");
        assertThat(pct).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void discountPct_alwaysWithinRange() {
        for (int dos = 1; dos <= 500; dos += 10) {
            double pct = service.calculateDiscountPct(dos, "default");
            assertThat(pct).isBetween(0.0, 40.0);
        }
    }

    // ── 弾力性係数テスト (D-F4-02) ──

    @Test
    void elasticity_usesConfiguredValue() {
        double pctDefault = service.calculateDiscountPct(200, "default");
        double pctWear = service.calculateDiscountPct(200, "cat-wear");
        // cat-wear has higher elasticity (-2.0 vs -1.5) → higher discount
        assertThat(pctWear).isGreaterThanOrEqualTo(pctDefault);
    }

    @Test
    void elasticity_fallsBackToDefault() {
        // unknown category → uses "default" elasticity
        double pct = service.calculateDiscountPct(200, "unknown-category");
        double pctDefault = service.calculateDiscountPct(200, "default");
        assertThat(pct).isEqualTo(pctDefault);
    }

    // ── D-F4-01: 境界値テスト ──

    @ParameterizedTest
    @CsvSource({ "30", "60", "90", "120", "180", "365" })
    void discountPct_variousDos_withinRange(int dos) {
        double pct = service.calculateDiscountPct(dos, "default");
        assertThat(pct).isBetween(0.0, 40.0);
    }
}
