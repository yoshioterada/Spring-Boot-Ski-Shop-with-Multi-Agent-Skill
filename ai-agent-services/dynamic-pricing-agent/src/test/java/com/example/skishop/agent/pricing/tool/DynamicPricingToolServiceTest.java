package com.example.skishop.agent.pricing.tool;

import com.example.skishop.agent.common.dto.SkiFeasibilityResult;
import com.example.skishop.agent.common.invoker.WeatherInvoker;
import com.example.skishop.agent.pricing.client.ProductCatalogClient;
import com.example.skishop.agent.pricing.client.SalesManagementClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DynamicPricingToolServiceTest {

    private ProductCatalogClient catalog;
    private SalesManagementClient sales;
    private WeatherInvoker weather;
    private DynamicPricingToolService tool;

    @BeforeEach
    void setUp() {
        catalog = mock(ProductCatalogClient.class);
        sales = mock(SalesManagementClient.class);
        weather = mock(WeatherInvoker.class);
        tool = new DynamicPricingToolService(catalog, sales, weather);
    }

    @Test
    void getBasePrice_delegates() {
        when(catalog.getBasePrice("p1")).thenReturn(new BigDecimal("10000"));
        assertThat(tool.getBasePrice("p1")).isEqualByComparingTo("10000");
    }

    @Test
    void demand_VERY_HIGH_when_sales_50_or_more() {
        when(sales.getSalesCount("p1", 7)).thenReturn(60);
        var r = tool.applyDemandAdjustment(new BigDecimal("10000"), "p1");
        assertThat(r).isEqualByComparingTo("15000");
    }

    @Test
    void demand_HIGH_when_sales_20_to_49() {
        when(sales.getSalesCount("p1", 7)).thenReturn(25);
        var r = tool.applyDemandAdjustment(new BigDecimal("10000"), "p1");
        assertThat(r).isEqualByComparingTo("12000");
    }

    @Test
    void demand_NORMAL_when_sales_5_to_19() {
        when(sales.getSalesCount("p1", 7)).thenReturn(10);
        var r = tool.applyDemandAdjustment(new BigDecimal("10000"), "p1");
        assertThat(r).isEqualByComparingTo("10000");
    }

    @Test
    void demand_LOW_when_sales_under_5() {
        when(sales.getSalesCount("p1", 7)).thenReturn(2);
        var r = tool.applyDemandAdjustment(new BigDecimal("10000"), "p1");
        assertThat(r).isEqualByComparingTo("9000");
    }

    @Test
    void demand_handles_null_basePrice() {
        assertThat(tool.applyDemandAdjustment(null, "p1")).isEqualByComparingTo("0");
    }

    @Test
    void weather_skipped_when_resort_null_or_blank() {
        var p = new BigDecimal("10000");
        assertThat(tool.applyWeatherAdjustment(p, null)).isEqualByComparingTo("10000");
        assertThat(tool.applyWeatherAdjustment(p, "")).isEqualByComparingTo("10000");
    }

    @Test
    void weather_EXCELLENT_multiplies_1_15() {
        when(weather.getOverallCondition("Naeba")).thenReturn("EXCELLENT");
        assertThat(tool.applyWeatherAdjustment(new BigDecimal("10000"), "Naeba"))
                .isEqualByComparingTo("11500");
    }

    @Test
    void weather_GOOD_multiplies_1_05() {
        when(weather.getOverallCondition("X")).thenReturn("GOOD");
        assertThat(tool.applyWeatherAdjustment(new BigDecimal("10000"), "X"))
                .isEqualByComparingTo("10500");
    }

    @Test
    void weather_POOR_multiplies_0_80() {
        when(weather.getOverallCondition("X")).thenReturn("POOR");
        assertThat(tool.applyWeatherAdjustment(new BigDecimal("10000"), "X"))
                .isEqualByComparingTo("8000");
    }

    @Test
    void weather_unknown_condition_returns_unchanged() {
        when(weather.getOverallCondition("X")).thenReturn("WEIRD");
        assertThat(tool.applyWeatherAdjustment(new BigDecimal("10000"), "X"))
                .isEqualByComparingTo("10000");
    }

    @Test
    void weather_handles_null_condition() {
        when(weather.getOverallCondition("X")).thenReturn(null);
        assertThat(tool.applyWeatherAdjustment(new BigDecimal("10000"), "X"))
                .isEqualByComparingTo("10000");
    }

    @Test
    void weather_handles_null_price() {
        assertThat(tool.applyWeatherAdjustment(null, "Naeba")).isEqualByComparingTo("0");
    }

    @Test
    void inventory_SCARCE_when_under_5() {
        when(catalog.getStockCount("p1")).thenReturn(3);
        assertThat(tool.applyInventoryAdjustment(new BigDecimal("10000"), "p1"))
                .isEqualByComparingTo("11000");
    }

    @Test
    void inventory_NORMAL_when_5_to_50() {
        when(catalog.getStockCount("p1")).thenReturn(20);
        assertThat(tool.applyInventoryAdjustment(new BigDecimal("10000"), "p1"))
                .isEqualByComparingTo("10000");
    }

    @Test
    void inventory_ABUNDANT_when_over_50() {
        when(catalog.getStockCount("p1")).thenReturn(80);
        assertThat(tool.applyInventoryAdjustment(new BigDecimal("10000"), "p1"))
                .isEqualByComparingTo("9500");
    }

    @Test
    void inventory_handles_null_price() {
        assertThat(tool.applyInventoryAdjustment(null, "p1")).isEqualByComparingTo("0");
    }

    @Test
    void tier_PLATINUM_15_off() {
        assertThat(tool.applyCustomerTierDiscount(new BigDecimal("10000"), "PLATINUM"))
                .isEqualByComparingTo("8500");
    }

    @Test
    void tier_GOLD_10_off() {
        assertThat(tool.applyCustomerTierDiscount(new BigDecimal("10000"), "GOLD"))
                .isEqualByComparingTo("9000");
    }

    @Test
    void tier_SILVER_5_off() {
        assertThat(tool.applyCustomerTierDiscount(new BigDecimal("10000"), "silver"))
                .isEqualByComparingTo("9500");
    }

    @Test
    void tier_BRONZE_no_discount() {
        assertThat(tool.applyCustomerTierDiscount(new BigDecimal("10000"), "BRONZE"))
                .isEqualByComparingTo("10000");
    }

    @Test
    void tier_unknown_no_discount() {
        assertThat(tool.applyCustomerTierDiscount(new BigDecimal("10000"), "OTHER"))
                .isEqualByComparingTo("10000");
    }

    @Test
    void tier_null_treated_as_BRONZE() {
        assertThat(tool.applyCustomerTierDiscount(new BigDecimal("10000"), null))
                .isEqualByComparingTo("10000");
    }

    @Test
    void tier_handles_null_price() {
        assertThat(tool.applyCustomerTierDiscount(null, "GOLD")).isEqualByComparingTo("0");
    }

    // helper-direct test: ensures default branch in determineDemandMultiplier
    @Test
    void determineDemandMultiplier_boundary_values() {
        assertThat(DynamicPricingToolService.determineDemandMultiplier(50)).isEqualTo(1.50);
        assertThat(DynamicPricingToolService.determineDemandMultiplier(20)).isEqualTo(1.20);
        assertThat(DynamicPricingToolService.determineDemandMultiplier(5)).isEqualTo(1.00);
        assertThat(DynamicPricingToolService.determineDemandMultiplier(0)).isEqualTo(0.90);
    }

    // 補助 SkiFeasibilityResult 利用テスト
    @Test
    void ski_feasibility_can_be_constructed() {
        var f = new SkiFeasibilityResult("HIGH", 80, "COLD", "POWDER", "EXCELLENT");
        assertThat(f.overallCondition()).isEqualTo("EXCELLENT");
        assertThat(f.feasibility()).isEqualTo("HIGH");
    }
}
