package com.example.skishop.agent.pricing.tool;

import com.example.skishop.agent.common.invoker.WeatherInvoker;
import com.example.skishop.agent.pricing.client.ProductCatalogClient;
import com.example.skishop.agent.pricing.client.SalesManagementClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.lang.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class DynamicPricingToolService {

    private static final Logger log = LoggerFactory.getLogger(DynamicPricingToolService.class);

    static final double DEMAND_VERY_HIGH = 1.50;
    static final double DEMAND_HIGH      = 1.20;
    static final double DEMAND_NORMAL    = 1.00;
    static final double DEMAND_LOW       = 0.90;

    static final double WEATHER_EXCELLENT = 1.15;
    static final double WEATHER_GOOD      = 1.05;
    static final double WEATHER_POOR      = 0.80;

    static final double INVENTORY_SCARCE   = 1.10;
    static final double INVENTORY_NORMAL   = 1.00;
    static final double INVENTORY_ABUNDANT = 0.95;

    private final ProductCatalogClient catalogClient;
    private final SalesManagementClient salesClient;
    private final WeatherInvoker weatherClient;

    public DynamicPricingToolService(ProductCatalogClient catalogClient,
                                      SalesManagementClient salesClient,
                                      WeatherInvoker weatherClient) {
        this.catalogClient = catalogClient;
        this.salesClient = salesClient;
        this.weatherClient = weatherClient;
    }

    @Tool(description = "商品の標準（ベース）価格を取得する。Chain Step1。")
    public BigDecimal getBasePrice(@ToolParam(description = "商品 ID") String productId) {
        log.info("Tool getBasePrice: productId={}", productId);
        return catalogClient.getBasePrice(productId);
    }

    @Tool(description = """
            直近7日間の販売数から需要レベルを判定し、価格に係数を適用する。
            VERY_HIGH(1.5)/HIGH(1.2)/NORMAL(1.0)/LOW(0.9)
            """)
    public BigDecimal applyDemandAdjustment(
            @ToolParam(description = "ベース価格") BigDecimal basePrice,
            @ToolParam(description = "商品 ID") String productId) {
        log.info("Tool applyDemandAdjustment: productId={}", productId);
        if (basePrice == null) return BigDecimal.ZERO;
        int salesLast7Days = salesClient.getSalesCount(productId, 7);
        double multiplier = determineDemandMultiplier(salesLast7Days);
        return basePrice.multiply(BigDecimal.valueOf(multiplier)).setScale(0, RoundingMode.HALF_UP);
    }

    @Tool(description = """
            気象コンディションに基づき価格を調整する。
            EXCELLENT(1.15)/GOOD(1.05)/POOR(0.80) 他は 1.00。
            """)
    public BigDecimal applyWeatherAdjustment(
            @ToolParam(description = "需要調整後の価格") BigDecimal price,
            @ToolParam(description = "リゾート（省略可）") @Nullable String resortLocation) {
        log.info("Tool applyWeatherAdjustment: resort={}", resortLocation);
        if (price == null) return BigDecimal.ZERO;
        if (resortLocation == null || resortLocation.isBlank()) return price;

        String condition = weatherClient.getOverallCondition(resortLocation);
        double multiplier = switch (condition == null ? "" : condition) {
            case "EXCELLENT" -> WEATHER_EXCELLENT;
            case "GOOD"      -> WEATHER_GOOD;
            case "POOR"      -> WEATHER_POOR;
            default          -> 1.00;
        };
        return price.multiply(BigDecimal.valueOf(multiplier)).setScale(0, RoundingMode.HALF_UP);
    }

    @Tool(description = """
            在庫残量に基づき価格を調整する。
            SCARCE(<5,1.10)/NORMAL(1.00)/ABUNDANT(>50,0.95)
            """)
    public BigDecimal applyInventoryAdjustment(
            @ToolParam(description = "気象調整後の価格") BigDecimal price,
            @ToolParam(description = "商品 ID") String productId) {
        log.info("Tool applyInventoryAdjustment: productId={}", productId);
        if (price == null) return BigDecimal.ZERO;
        int stockCount = catalogClient.getStockCount(productId);
        double multiplier = stockCount < 5 ? INVENTORY_SCARCE
                : stockCount > 50 ? INVENTORY_ABUNDANT
                : INVENTORY_NORMAL;
        return price.multiply(BigDecimal.valueOf(multiplier)).setScale(0, RoundingMode.HALF_UP);
    }

    @Tool(description = """
            顧客ティア割引を適用して最終価格を返す。
            PLATINUM:15%offGOLD:10%off/SILVER:5%off/BRONZE:0%off
            """)
    public BigDecimal applyCustomerTierDiscount(
            @ToolParam(description = "在庫調整後の価格") BigDecimal price,
            @ToolParam(description = "顧客ティア") String customerTier) {
        log.info("Tool applyCustomerTierDiscount: tier={}", customerTier);
        if (price == null) return BigDecimal.ZERO;
        String tier = customerTier == null ? "BRONZE" : customerTier.toUpperCase();
        double discountRate = switch (tier) {
            case "PLATINUM" -> 0.85;
            case "GOLD"     -> 0.90;
            case "SILVER"   -> 0.95;
            default         -> 1.00;
        };
        return price.multiply(BigDecimal.valueOf(discountRate)).setScale(0, RoundingMode.HALF_UP);
    }

    static double determineDemandMultiplier(int salesLast7Days) {
        if (salesLast7Days >= 50) return DEMAND_VERY_HIGH;
        if (salesLast7Days >= 20) return DEMAND_HIGH;
        if (salesLast7Days >= 5)  return DEMAND_NORMAL;
        return DEMAND_LOW;
    }
}
