package com.example.skishop.ai.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * F3 週次サマリー MongoDB ドキュメント (collection: weekly_summaries).
 * <p>
 * TTL は {@code ttl} フィールドで管理し、MongoDB の TTL インデックスで 90 日後に自動削除される (D-F3-02).
 */
@Document(collection = "weekly_summaries")
public class WeeklySummary {

    @Id
    private String id;

    @Indexed(unique = true)
    private LocalDate weekStartDate;

    private LocalDate weekEndDate;
    private Instant generatedAt;

    private long revenue;
    private long orders;
    private long uniqueCustomers;
    private long avgOrderValue;

    private double revenueWow;
    private double revenueYoy;
    private double ordersWow;
    private double ordersYoy;
    private double uniqueCustomersWow;
    private double uniqueCustomersYoy;
    private double avgOrderValueWow;
    private double avgOrderValueYoy;

    private List<HighlightEntry> highlights;
    private String narrative;
    private List<ProductMovementEntry> topRisingProducts;
    private List<ProductMovementEntry> topFallingProducts;

    /** TTL フィールド — expireAfterSeconds: 0 のインデックスで使用 */
    private Instant ttl;

    public WeeklySummary() {
    }

    // --- Inner records ---
    public record HighlightEntry(String icon, String text) {}
    public record ProductMovementEntry(String sku, String name, long sales, double changeRate) {}

    // --- Getters / Setters ---
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public LocalDate getWeekStartDate() { return weekStartDate; }
    public void setWeekStartDate(LocalDate weekStartDate) { this.weekStartDate = weekStartDate; }

    public LocalDate getWeekEndDate() { return weekEndDate; }
    public void setWeekEndDate(LocalDate weekEndDate) { this.weekEndDate = weekEndDate; }

    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }

    public long getRevenue() { return revenue; }
    public void setRevenue(long revenue) { this.revenue = revenue; }

    public long getOrders() { return orders; }
    public void setOrders(long orders) { this.orders = orders; }

    public long getUniqueCustomers() { return uniqueCustomers; }
    public void setUniqueCustomers(long uniqueCustomers) { this.uniqueCustomers = uniqueCustomers; }

    public long getAvgOrderValue() { return avgOrderValue; }
    public void setAvgOrderValue(long avgOrderValue) { this.avgOrderValue = avgOrderValue; }

    public double getRevenueWow() { return revenueWow; }
    public void setRevenueWow(double revenueWow) { this.revenueWow = revenueWow; }

    public double getRevenueYoy() { return revenueYoy; }
    public void setRevenueYoy(double revenueYoy) { this.revenueYoy = revenueYoy; }

    public double getOrdersWow() { return ordersWow; }
    public void setOrdersWow(double ordersWow) { this.ordersWow = ordersWow; }

    public double getOrdersYoy() { return ordersYoy; }
    public void setOrdersYoy(double ordersYoy) { this.ordersYoy = ordersYoy; }

    public double getUniqueCustomersWow() { return uniqueCustomersWow; }
    public void setUniqueCustomersWow(double uniqueCustomersWow) { this.uniqueCustomersWow = uniqueCustomersWow; }

    public double getUniqueCustomersYoy() { return uniqueCustomersYoy; }
    public void setUniqueCustomersYoy(double uniqueCustomersYoy) { this.uniqueCustomersYoy = uniqueCustomersYoy; }

    public double getAvgOrderValueWow() { return avgOrderValueWow; }
    public void setAvgOrderValueWow(double avgOrderValueWow) { this.avgOrderValueWow = avgOrderValueWow; }

    public double getAvgOrderValueYoy() { return avgOrderValueYoy; }
    public void setAvgOrderValueYoy(double avgOrderValueYoy) { this.avgOrderValueYoy = avgOrderValueYoy; }

    public List<HighlightEntry> getHighlights() { return highlights; }
    public void setHighlights(List<HighlightEntry> highlights) { this.highlights = highlights; }

    public String getNarrative() { return narrative; }
    public void setNarrative(String narrative) { this.narrative = narrative; }

    public List<ProductMovementEntry> getTopRisingProducts() { return topRisingProducts; }
    public void setTopRisingProducts(List<ProductMovementEntry> topRisingProducts) { this.topRisingProducts = topRisingProducts; }

    public List<ProductMovementEntry> getTopFallingProducts() { return topFallingProducts; }
    public void setTopFallingProducts(List<ProductMovementEntry> topFallingProducts) { this.topFallingProducts = topFallingProducts; }

    public Instant getTtl() { return ttl; }
    public void setTtl(Instant ttl) { this.ttl = ttl; }
}
