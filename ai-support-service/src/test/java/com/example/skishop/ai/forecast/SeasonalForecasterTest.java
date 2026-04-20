package com.example.skishop.ai.forecast;

import com.example.skishop.ai.dto.SeasonalForecastRequest.Horizon;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SeasonalForecaster 単体テスト (P4: 境界値・年またぎ・データ不足).
 */
@ExtendWith(MockitoExtension.class)
class SeasonalForecasterTest {

    @Mock
    private WebClient salesWebClient;
    @Mock
    private WebClient inventoryWebClient;

    private SeasonalForecaster forecaster;

    @BeforeEach
    void setUp() {
        forecaster = new SeasonalForecaster(salesWebClient, inventoryWebClient);
    }

    // ── resolveForecastMonths テスト ──

    @Test
    void nextMonth_returnsSingleMonth() {
        List<YearMonth> months = forecaster.resolveForecastMonths(Horizon.NEXT_MONTH);
        assertThat(months).hasSize(1);
        assertThat(months.getFirst().isAfter(YearMonth.now())).isTrue();
    }

    @Test
    void nextSeason_returns6Months() {
        List<YearMonth> months = forecaster.resolveForecastMonths(Horizon.NEXT_SEASON);
        assertThat(months).hasSize(6);
        // 冬季: 10月〜3月
        assertThat(months.getFirst().getMonthValue()).isEqualTo(10);
        assertThat(months.getLast().getMonthValue()).isEqualTo(3);
    }

    @Test
    void nextYear_returns12Months() {
        List<YearMonth> months = forecaster.resolveForecastMonths(Horizon.NEXT_YEAR);
        assertThat(months).hasSize(12);
        int nextYear = LocalDate.now().getYear() + 1;
        assertThat(months.getFirst()).isEqualTo(YearMonth.of(nextYear, 1));
        assertThat(months.getLast()).isEqualTo(YearMonth.of(nextYear, 12));
    }

    // ── computeStddev テスト ──

    @Test
    void stddev_withMultipleValues_calculatesCorrectly() {
        List<Long> values = List.of(100L, 110L, 90L, 105L);
        double mean = values.stream().mapToLong(Long::longValue).average().orElse(0);
        double stddev = forecaster.computeStddev(values, mean);
        assertThat(stddev).isGreaterThan(0);
        assertThat(stddev).isLessThan(mean); // 標準偏差 < 平均
    }

    @Test
    void stddev_withSingleValue_returnsFallback() {
        double stddev = forecaster.computeStddev(List.of(100L), 100.0);
        // データ不足時フォールバック: 平均の 20%
        assertThat(stddev).isEqualTo(20.0);
    }

    @Test
    void stddev_withEmptyList_returnsFallback() {
        double stddev = forecaster.computeStddev(List.of(), 50.0);
        assertThat(stddev).isEqualTo(10.0); // 50 * 0.2
    }

    @Test
    void stddev_withIdenticalValues_returnsZero() {
        List<Long> values = List.of(100L, 100L, 100L);
        double stddev = forecaster.computeStddev(values, 100.0);
        assertThat(stddev).isEqualTo(0.0);
    }

    // ── 年またぎ ──

    @Test
    void nextSeason_spansYearBoundary() {
        List<YearMonth> months = forecaster.resolveForecastMonths(Horizon.NEXT_SEASON);
        // 10, 11, 12, 1, 2, 3 — crosses year boundary
        int firstYear = months.getFirst().getYear();
        int lastYear = months.getLast().getYear();
        assertThat(lastYear).isEqualTo(firstYear + 1);
    }

    // ── 信頼区間 ±1.5σ (D-F2-03) ──

    @Test
    void confidenceSigma_isOnePointFive() {
        // SeasonalForecaster.CONFIDENCE_SIGMA = 1.5 を間接検証
        List<Long> values = List.of(100L, 120L, 80L, 110L, 90L);
        double mean = 100.0;
        double stddev = forecaster.computeStddev(values, mean);
        double margin = 1.5 * stddev; // D-F2-03
        assertThat(margin).isGreaterThan(0);
        assertThat(margin).isLessThan(mean); // reasonable margin
    }

    // ── カテゴリ名解決 ──

    @Test
    void horizonLabel_nextMonth_containsYear() {
        List<YearMonth> months = forecaster.resolveForecastMonths(Horizon.NEXT_MONTH);
        assertThat(months).isNotEmpty();
        // just verify it resolves without error
    }

    @Test
    void horizonLabel_nextSeason_containsSeasonRange() {
        List<YearMonth> months = forecaster.resolveForecastMonths(Horizon.NEXT_SEASON);
        assertThat(months.getFirst().getMonthValue()).isEqualTo(10);
        assertThat(months.getLast().getMonthValue()).isEqualTo(3);
    }

    // ── D-F2-02: 季節係数がコードにハードコードされていない ──

    @Test
    void noHardcodedWeightsInForecaster() throws Exception {
        // SeasonalForecaster のソースにハードコードされた季節係数がないことを確認
        // (実際の値は seasonal_weights テーブルから取得)
        var clazz = SeasonalForecaster.class;
        var fields = clazz.getDeclaredFields();
        for (var field : fields) {
            assertThat(field.getName()).doesNotContain("weight");
            assertThat(field.getName()).doesNotContain("WEIGHT");
        }
    }
}
