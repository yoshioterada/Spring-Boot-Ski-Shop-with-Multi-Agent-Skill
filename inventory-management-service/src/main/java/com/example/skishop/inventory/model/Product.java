package com.example.skishop.inventory.model;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Document(collection = "products")
public class Product {

    @Id
    private String id;

    @Indexed(unique = true)
    private String sku;

    private String name;
    private String description;
    private String brand;

    @Indexed
    private String categoryId;

    private Map<String, String> attributes;
    private List<String> tags = new ArrayList<>();
    private List<ProductImage> images = new ArrayList<>();

    private BigDecimal regularPrice;
    private BigDecimal salePrice;
    private Instant saleStartDate;
    private Instant saleEndDate;
    private String currency = "JPY";

    private int stockQuantity;
    private int reservedQuantity;
    private String locationCode;

    @Indexed
    private ProductStatus status = ProductStatus.ACTIVE;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    public Product() {}

    public Product(String sku, String name, String brand, String categoryId) {
        this.sku = sku;
        this.name = name;
        this.brand = brand;
        this.categoryId = categoryId;
    }

    public int getAvailableQuantity() {
        return stockQuantity - reservedQuantity;
    }

    public String getId() { return id; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getBrand() { return brand; }
    public String getCategoryId() { return categoryId; }
    public Map<String, String> getAttributes() { return attributes; }
    public List<String> getTags() { return tags; }
    public List<ProductImage> getImages() { return images; }
    public BigDecimal getRegularPrice() { return regularPrice; }
    public BigDecimal getSalePrice() { return salePrice; }
    public Instant getSaleStartDate() { return saleStartDate; }
    public Instant getSaleEndDate() { return saleEndDate; }
    public String getCurrency() { return currency; }
    public int getStockQuantity() { return stockQuantity; }
    public int getReservedQuantity() { return reservedQuantity; }
    public String getLocationCode() { return locationCode; }
    public ProductStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setSku(String sku) { this.sku = sku; }
    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setBrand(String brand) { this.brand = brand; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }
    public void setAttributes(Map<String, String> attributes) { this.attributes = attributes; }
    public void setTags(List<String> tags) { this.tags = tags; }
    public void setImages(List<ProductImage> images) { this.images = images; }
    public void setRegularPrice(BigDecimal regularPrice) { this.regularPrice = regularPrice; }
    public void setSalePrice(BigDecimal salePrice) { this.salePrice = salePrice; }
    public void setSaleStartDate(Instant saleStartDate) { this.saleStartDate = saleStartDate; }
    public void setSaleEndDate(Instant saleEndDate) { this.saleEndDate = saleEndDate; }
    public void setCurrency(String currency) { this.currency = currency; }
    public void setStockQuantity(int stockQuantity) { this.stockQuantity = stockQuantity; }
    public void setReservedQuantity(int reservedQuantity) { this.reservedQuantity = reservedQuantity; }
    public void setLocationCode(String locationCode) { this.locationCode = locationCode; }
    public void setStatus(ProductStatus status) { this.status = status; }

    public enum ProductStatus {
        ACTIVE, INACTIVE, DISCONTINUED
    }

    public record ProductImage(String url, String thumbnailUrl, String type, int sortOrder) {}
}
