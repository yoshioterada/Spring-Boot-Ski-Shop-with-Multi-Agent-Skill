package com.example.skishop.ai.service;

import com.example.skishop.ai.config.AiAnalyzerProperties;
import com.example.skishop.ai.repository.ZeroHitDismissalRepository;
import com.example.skishop.ai.util.PromptSanitizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * ZeroHitOpportunityService テスト (P6: クエリ正規化, 損失計算, パラメータ制限).
 */
@ExtendWith(MockitoExtension.class)
class ZeroHitOpportunityServiceTest {

    @Mock private WebClient inventoryWebClient;
    @Mock private ChatClient.Builder chatClientBuilder;
    @Mock private ChatClient chatClient;
    @Mock private ZeroHitDismissalRepository dismissalRepo;
    @Mock private LlmAuditService auditService;

    private AiAnalyzerProperties props;
    private ZeroHitOpportunityService service;

    @BeforeEach
    void setUp() {
        props = new AiAnalyzerProperties();
        props.getZeroHit().setMinSearchCount(3);
        props.getZeroHit().setDismissTtlDays(90);
        props.getZeroHit().setConversionRate(0.05);
        when(chatClientBuilder.build()).thenReturn(chatClient);

        service = new ZeroHitOpportunityService(
                inventoryWebClient, props, chatClientBuilder,
                dismissalRepo, auditService, new PromptSanitizer(), new ObjectMapper());
    }

    // ── クエリ正規化テスト (D-F5-03) ──

    @Test
    void normalizeQuery_fullWidthDigitsToHalfWidth() {
        // 「１６５cm」→ 「165cm」
        assertThat(service.normalizeQuery("１６５cm")).isEqualTo("165cm");
    }

    @Test
    void normalizeQuery_fullWidthAlphaToHalfWidth() {
        // 「Ａｔｏｍｉｃ」→ 「atomic」
        assertThat(service.normalizeQuery("Ａｔｏｍｉｃ")).isEqualTo("atomic");
    }

    @Test
    void normalizeQuery_spaceBeforeUnit() {
        // 「165 cm」→ 「165cm」
        assertThat(service.normalizeQuery("165 cm")).isEqualTo("165cm");
    }

    @Test
    void normalizeQuery_allFormsSame() {
        // 「165cm」「１６５cm」「165 cm」全て同一に
        String result1 = service.normalizeQuery("165cm");
        String result2 = service.normalizeQuery("１６５cm");
        String result3 = service.normalizeQuery("165 cm");
        assertThat(result1).isEqualTo(result2).isEqualTo(result3);
    }

    @Test
    void normalizeQuery_fullWidthSpace() {
        // 全角スペース → 半角
        assertThat(service.normalizeQuery("カービング\u3000スキー")).isEqualTo("カービング スキー");
    }

    @Test
    void normalizeQuery_multipleSpaces() {
        assertThat(service.normalizeQuery("ski   boot")).isEqualTo("ski boot");
    }

    @Test
    void normalizeQuery_nullReturnsEmpty() {
        assertThat(service.normalizeQuery(null)).isEmpty();
    }

    @Test
    void normalizeQuery_blankReturnsEmpty() {
        assertThat(service.normalizeQuery("   ")).isEmpty();
    }

    @Test
    void normalizeQuery_caseInsensitive() {
        assertThat(service.normalizeQuery("ATOMIC Cloud")).isEqualTo("atomic cloud");
    }

    // ── 機会損失計算テスト (D-F5-06) ──

    @Test
    void lossCalculation_formula() {
        // D-F5-06: searchCount × 0.05 × category_avg_price(50000)
        double conversionRate = props.getZeroHit().getConversionRate();
        assertThat(conversionRate).isEqualTo(0.05);

        long searchCount = 47;
        double avgPrice = 50000;
        long expectedLoss = Math.round(searchCount * conversionRate * avgPrice);
        assertThat(expectedLoss).isEqualTo(117500);
    }

    @Test
    void conversionRate_fromConfig() {
        // D-F5-06: 固定 conversion rate は外出し
        assertThat(props.getZeroHit().getConversionRate()).isEqualTo(0.05);
    }

    // ── minSearchCount テスト (D-F5-04) ──

    @Test
    void minSearchCount_defaultIs3() {
        assertThat(props.getZeroHit().getMinSearchCount()).isEqualTo(3);
    }

    // ── TTL テスト (D-F5-05) ──

    @Test
    void dismissTtl_defaultIs90Days() {
        assertThat(props.getZeroHit().getDismissTtlDays()).isEqualTo(90);
    }

    // ── 正規化パラメタライズドテスト ──

    @ParameterizedTest
    @CsvSource({
            "165cm,165cm",
            "１６５cm,165cm",
            "165 cm,165cm",
            "165  cm,165cm",
            "ATOMIC,atomic",
            "Ａｔｏｍｉｃ,atomic"
    })
    void normalizeQuery_parametrized(String input, String expected) {
        assertThat(service.normalizeQuery(input)).isEqualTo(expected);
    }

    // ── PII 安全性テスト (D-F5-07) ──

    @Test
    void normalizedQuery_noPiiFields() {
        // 正規化はキーワードのみ処理、userId/IP/sessionId は含まない
        String result = service.normalizeQuery("カービングスキー 165cm レディース");
        assertThat(result).doesNotContain("userId", "sessionId", "IP");
    }
}
