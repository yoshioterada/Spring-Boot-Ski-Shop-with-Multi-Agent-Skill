package com.example.skishop.agent.equipment.tool;

import com.example.skishop.agent.common.dto.ProductCandidate;
import com.example.skishop.agent.common.dto.RankedProduct;
import com.example.skishop.agent.common.dto.SkiFeasibilityResult;
import com.example.skishop.agent.common.invoker.WeatherInvoker;
import com.example.skishop.agent.equipment.client.InventoryClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EquipmentMatchingToolServiceTest {

    private InventoryClient inventoryClient;
    private WeatherInvoker weatherInvoker;
    private EquipmentMatchingToolService tool;

    @BeforeEach
    void setUp() {
        inventoryClient = mock(InventoryClient.class);
        weatherInvoker = mock(WeatherInvoker.class);
        tool = new EquipmentMatchingToolService(inventoryClient, weatherInvoker);
    }

    private ProductCandidate ski(String id, String length, String suit, String weather, int price, boolean avail) {
        return new ProductCandidate(id, "Ski " + id, "スキー板", "BrandX",
                BigDecimal.valueOf(price), avail, avail ? 5 : 0, suit, weather,
                Map.of("length", length));
    }

    @Test
    void searchInventoryCandidates_delegates_to_client() {
        // 入力 "スキー板" は normalizeCategory で "cat-ski" に変換され、その値で client が呼ばれる
        when(inventoryClient.searchBySkillAndCategory("cat-ski", "BEGINNER", 50000))
                .thenReturn(List.of(ski("p1", "150cm", "BEGINNER", "ALL_CONDITIONS", 30000, true)));
        var result = tool.searchInventoryCandidates("スキー板", "BEGINNER", 50000);
        assertThat(result).hasSize(1);
    }

    @Test
    void filterByBodyMeasurements_keeps_skis_within_height_range() {
        // height=170cm → 144.5〜170cm の長さの板のみ通過
        var fit = ski("p1", "160cm", "ALL", "ALL_CONDITIONS", 50000, true);
        var tooLong = ski("p2", "180cm", "ALL", "ALL_CONDITIONS", 50000, true);
        var tooShort = ski("p3", "100cm", "ALL", "ALL_CONDITIONS", 50000, true);

        var filtered = tool.filterByBodyMeasurements(List.of(fit, tooLong, tooShort), 170, 65, 26.5);
        assertThat(filtered).extracting(ProductCandidate::productId).containsExactly("p1");
    }

    @Test
    void filterByBodyMeasurements_passes_when_height_null() {
        var p = ski("p1", "180cm", "ALL", "ALL_CONDITIONS", 50000, true);
        assertThat(tool.filterByBodyMeasurements(List.of(p), null, null, null)).hasSize(1);
    }

    @Test
    void filterByBodyMeasurements_passes_for_non_ski_category() {
        var boot = new ProductCandidate("b1", "Boot", "ブーツ", "B",
                BigDecimal.TEN, true, 1, "ALL", "ALL_CONDITIONS", Map.of());
        assertThat(tool.filterByBodyMeasurements(List.of(boot), 170, 65, 26.5)).hasSize(1);
    }

    @Test
    void filterByBodyMeasurements_handles_invalid_length_attribute() {
        var bad = ski("p1", "abc", "ALL", "ALL_CONDITIONS", 50000, true);
        assertThat(tool.filterByBodyMeasurements(List.of(bad), 170, 65, 26.5)).hasSize(1);
    }

    @Test
    void filterByBodyMeasurements_handles_null_candidates() {
        assertThat(tool.filterByBodyMeasurements(null, 170, 65, 26.5)).isEmpty();
    }

    @Test
    void scoreByWeather_powder_prioritizes_powder_products() {
        when(weatherInvoker.getFeasibility("Naeba"))
                .thenReturn(new SkiFeasibilityResult("HIGH", 80, "COLD", "POWDER", "EXCELLENT"));
        var allCond = ski("a", "160cm", "ALL", "ALL_CONDITIONS", 50000, true);
        var powder = ski("p", "160cm", "ALL", "POWDER", 50000, true);
        var groomed = ski("g", "160cm", "ALL", "GROOMED", 50000, true);

        var sorted = tool.scoreByWeatherConditions(List.of(allCond, groomed, powder), "Naeba");
        assertThat(sorted.get(0).productId()).isEqualTo("p");
    }

    @Test
    void scoreByWeather_handles_null_feasibility_result() {
        when(weatherInvoker.getFeasibility("X")).thenReturn(null);
        var p = ski("p", "160cm", "ALL", "ALL_CONDITIONS", 50000, true);
        var result = tool.scoreByWeatherConditions(List.of(p), "X");
        assertThat(result).hasSize(1);
    }

    @Test
    void scoreByWeather_handles_empty_candidates() {
        assertThat(tool.scoreByWeatherConditions(List.of(), "Naeba")).isEmpty();
        assertThat(tool.scoreByWeatherConditions(null, "Naeba")).isEmpty();
    }

    @Test
    void rankProducts_excludes_over_budget() {
        var cheap = ski("c", "160cm", "BEGINNER", "ALL_CONDITIONS", 30000, true);
        var expensive = ski("e", "160cm", "BEGINNER", "ALL_CONDITIONS", 100000, true);

        List<RankedProduct> ranked = tool.rankProducts(List.of(cheap, expensive), "BEGINNER", 50000);
        assertThat(ranked).extracting(rp -> rp.product().productId()).containsExactly("c");
        assertThat(ranked.get(0).rank()).isEqualTo(1);
    }

    @Test
    void rankProducts_no_budget_includes_all() {
        var p1 = ski("p1", "160cm", "BEGINNER", "ALL_CONDITIONS", 30000, true);
        var p2 = ski("p2", "160cm", "ALL", "ALL_CONDITIONS", 100000, true);
        var ranked = tool.rankProducts(List.of(p1, p2), "BEGINNER", null);
        assertThat(ranked).hasSize(2);
        // BEGINNER 適合の p1 が高スコア
        assertThat(ranked.get(0).product().productId()).isEqualTo("p1");
    }

    @Test
    void rankProducts_handles_null_candidates() {
        assertThat(tool.rankProducts(null, "BEGINNER", null)).isEmpty();
    }

    @Test
    void rankProducts_skips_null_basePrice_in_budget_check() {
        var p = new ProductCandidate("x", "X", "スキー板", "B", null, true, 1, "BEGINNER", "ALL_CONDITIONS", Map.of());
        var ranked = tool.rankProducts(List.of(p), "BEGINNER", 50000);
        assertThat(ranked).hasSize(1);
    }
}
