package com.example.skishop.ai.service;

import com.example.skishop.ai.config.AiAnalyzerProperties;
import com.example.skishop.ai.dto.WeeklySummaryResponse;
import com.example.skishop.ai.model.WeeklySummary;
import com.example.skishop.ai.repository.WeeklySummaryRepository;
import com.example.skishop.ai.util.PromptSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * WeeklySummaryService の単体テスト — P1-9 / P2 対応 (分岐カバレッジ ≥ 80%).
 */
@ExtendWith(MockitoExtension.class)
class WeeklySummaryServiceTest {

    @Mock
    private WeeklySummaryRepository repository;
    @Mock
    private WebClient salesWebClient;
    @Mock
    private WebClient userWebClient;
    @Mock
    private ChatClient.Builder chatClientBuilder;
    @Mock
    private ChatClient chatClient;
    @Mock
    private LlmAuditService auditService;
    @Mock
    private ZeroHitOpportunityService zeroHitService;

    private AiAnalyzerProperties props;
    private PromptSanitizer promptSanitizer;
    private WeeklySummaryService service;

    @BeforeEach
    void setUp() {
        props = new AiAnalyzerProperties();
        props.getWeeklySummary().setRefreshRateLimitMinutes(60);
        promptSanitizer = new PromptSanitizer();
        when(chatClientBuilder.build()).thenReturn(chatClient);
        service = new WeeklySummaryService(repository, salesWebClient, userWebClient,
                props, chatClientBuilder, auditService, promptSanitizer, zeroHitService);
    }

    // ----- ratio() -----

    @Test
    void ratio_normalCase() {
        assertThat(WeeklySummaryService.ratio(120, 100)).isCloseTo(0.2, within(0.001));
    }

    @Test
    void ratio_previousZero_currentPositive() {
        assertThat(WeeklySummaryService.ratio(100, 0)).isEqualTo(1.0);
    }

    @Test
    void ratio_previousZero_currentZero() {
        assertThat(WeeklySummaryService.ratio(0, 0)).isEqualTo(0.0);
    }

    @Test
    void ratio_decrease() {
        assertThat(WeeklySummaryService.ratio(80, 100)).isCloseTo(-0.2, within(0.001));
    }

    @Test
    void ratio_noChange() {
        assertThat(WeeklySummaryService.ratio(100, 100)).isCloseTo(0.0, within(0.001));
    }

    // ----- toLong() -----

    @Test
    void toLong_fromInteger() {
        assertThat(WeeklySummaryService.toLong(Map.of("k", 42), "k")).isEqualTo(42);
    }

    @Test
    void toLong_fromLong() {
        assertThat(WeeklySummaryService.toLong(Map.of("k", 9999999999L), "k")).isEqualTo(9999999999L);
    }

    @Test
    void toLong_fromDouble() {
        assertThat(WeeklySummaryService.toLong(Map.of("k", 42.9), "k")).isEqualTo(42);
    }

    @Test
    void toLong_fromString() {
        assertThat(WeeklySummaryService.toLong(Map.of("k", "123"), "k")).isEqualTo(123);
    }

    @Test
    void toLong_fromInvalidString() {
        assertThat(WeeklySummaryService.toLong(Map.of("k", "abc"), "k")).isEqualTo(0);
    }

    @Test
    void toLong_missingKey() {
        assertThat(WeeklySummaryService.toLong(Map.of(), "k")).isEqualTo(0);
    }

    @Test
    void toLong_nullValue() {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("k", null);
        assertThat(WeeklySummaryService.toLong(map, "k")).isEqualTo(0);
    }

    // ----- currentWeekStart() -----

    @Test
    void currentWeekStart_isMonday() {
        LocalDate ws = WeeklySummaryService.currentWeekStart();
        assertThat(ws.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
    }

    // ----- getWeeklySummary() cache hit -----

    @Test
    void getWeeklySummary_cacheHit_returnsFromMongo() {
        WeeklySummary doc = buildDoc();
        when(repository.findByWeekStartDate(any())).thenReturn(Optional.of(doc));

        WeeklySummaryResponse resp = service.getWeeklySummary();

        assertThat(resp.cacheHit()).isTrue();
        assertThat(resp.narrative()).isNotNull();
        verify(repository, times(1)).findByWeekStartDate(any());
        verify(repository, never()).save(any());
    }

    // ----- refreshWeeklySummary() rate limit -----

    @Test
    void refreshWeeklySummary_secondCallWithinLimit_returns429() {
        WeeklySummary doc = buildDoc();
        when(repository.findByWeekStartDate(any())).thenReturn(Optional.of(doc));

        // First call should succeed
        service.refreshWeeklySummary();
        // Second call within 60 min should be empty (429)
        Optional<WeeklySummaryResponse> second = service.refreshWeeklySummary();

        assertThat(second).isEmpty();
    }

    // ----- Response schema validation -----

    @Test
    void response_hasAllRequiredFields() {
        WeeklySummary doc = buildDoc();
        when(repository.findByWeekStartDate(any())).thenReturn(Optional.of(doc));

        WeeklySummaryResponse resp = service.getWeeklySummary();

        assertThat(resp.weekStart()).isNotNull();
        assertThat(resp.weekEnd()).isNotNull();
        assertThat(resp.generatedAt()).isNotNull();
        assertThat(resp.kpis()).isNotNull();
        assertThat(resp.kpis().revenue()).isNotNull();
        assertThat(resp.kpis().orders()).isNotNull();
        assertThat(resp.kpis().uniqueCustomers()).isNotNull();
        assertThat(resp.kpis().avgOrderValue()).isNotNull();
        assertThat(resp.highlights()).isNotNull();
        assertThat(resp.narrative()).isNotNull();
        assertThat(resp.topRisingProducts()).isNotNull();
        assertThat(resp.topFallingProducts()).isNotNull();
    }

    // ----- Year boundary (年またぎ) -----

    @Test
    void ratio_yearBoundary_largeDecrease() {
        // Simulates: this week = 10, last year same week = 1000
        assertThat(WeeklySummaryService.ratio(10, 1000)).isCloseTo(-0.99, within(0.001));
    }

    @Test
    void ratio_yearBoundary_largeIncrease() {
        assertThat(WeeklySummaryService.ratio(1000, 10)).isCloseTo(99.0, within(0.1));
    }

    // ----- Helpers -----

    private WeeklySummary buildDoc() {
        WeeklySummary doc = new WeeklySummary();
        LocalDate ws = WeeklySummaryService.currentWeekStart();
        doc.setWeekStartDate(ws);
        doc.setWeekEndDate(ws.plusDays(6));
        doc.setGeneratedAt(Instant.now());
        doc.setRevenue(28500000);
        doc.setOrders(532);
        doc.setUniqueCustomers(410);
        doc.setAvgOrderValue(53571);
        doc.setRevenueWow(-0.04);
        doc.setRevenueYoy(0.12);
        doc.setOrdersWow(0.02);
        doc.setOrdersYoy(0.08);
        doc.setUniqueCustomersWow(0.05);
        doc.setUniqueCustomersYoy(0.15);
        doc.setAvgOrderValueWow(-0.06);
        doc.setAvgOrderValueYoy(0.04);
        doc.setHighlights(List.of(
                new WeeklySummary.HighlightEntry("📈", "テスト用ハイライト")));
        doc.setNarrative("テスト用ナラティブ：今週の売上は好調でした。");
        doc.setTopRisingProducts(List.of());
        doc.setTopFallingProducts(List.of());
        doc.setTtl(Instant.now().plusSeconds(86400 * 90));
        return doc;
    }
}
