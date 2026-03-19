package com.example.skishop.inventory.service;

import com.example.skishop.common.event.DomainEvent;
import com.example.skishop.common.event.EventPublisher;
import com.example.skishop.common.exception.BusinessRuleViolationException;
import com.example.skishop.common.exception.ResourceNotFoundException;
import com.example.skishop.inventory.dto.*;
import com.example.skishop.inventory.model.Product;
import com.example.skishop.inventory.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);
    private static final int LOW_STOCK_THRESHOLD = 10;

    private final ProductRepository productRepository;
    private final EventPublisher eventPublisher;

    public ProductService(ProductRepository productRepository, EventPublisher eventPublisher) {
        this.productRepository = productRepository;
        this.eventPublisher = eventPublisher;
    }

    public ProductResponse createProduct(CreateProductRequest request) {
        log.info("Creating product with SKU: {}", request.sku());

        if (productRepository.existsBySku(request.sku())) {
            throw new BusinessRuleViolationException("SKU_ALREADY_EXISTS",
                    "このSKUは既に登録されています: " + request.sku());
        }

        var product = new Product(request.sku(), request.name(), request.brand(), request.categoryId());
        product.setDescription(request.description());
        product.setRegularPrice(request.regularPrice());
        product.setStockQuantity(request.initialStock());
        product.setLocationCode(request.locationCode());
        product.setAttributes(request.attributes());
        product.setTags(request.tags());

        product = productRepository.save(product);
        log.info("Product created: {}", product.getId());

        eventPublisher.publish(DomainEvent.create("ProductCreated", "inventory-service",
                new ProductEventPayload(product.getId(), product.getSku(), product.getName())));

        return toResponse(product);
    }

    public ProductResponse updateProduct(String productId, UpdateProductRequest request) {
        log.info("Updating product: {}", productId);
        var product = findProductOrThrow(productId);

        if (request.name() != null) { product.setName(request.name()); }
        if (request.description() != null) { product.setDescription(request.description()); }
        if (request.brand() != null) { product.setBrand(request.brand()); }
        if (request.categoryId() != null) { product.setCategoryId(request.categoryId()); }
        if (request.regularPrice() != null) { product.setRegularPrice(request.regularPrice()); }
        if (request.attributes() != null) { product.setAttributes(request.attributes()); }
        if (request.tags() != null) { product.setTags(request.tags()); }

        product = productRepository.save(product);
        log.info("Product updated: {}", product.getId());

        eventPublisher.publish(DomainEvent.create("ProductUpdated", "inventory-service",
                new ProductEventPayload(product.getId(), product.getSku(), product.getName())));

        return toResponse(product);
    }

    public void deleteProduct(String productId) {
        log.info("Soft-deleting product: {}", productId);
        var product = findProductOrThrow(productId);

        if (product.getReservedQuantity() > 0) {
            throw new BusinessRuleViolationException("PRODUCT_HAS_RESERVATIONS",
                    "予約中の在庫があるため削除できません: " + productId);
        }

        product.setStatus(Product.ProductStatus.DISCONTINUED);
        productRepository.save(product);
        log.info("Product soft-deleted: {}", productId);

        eventPublisher.publish(DomainEvent.create("ProductDeleted", "inventory-service",
                new ProductEventPayload(product.getId(), product.getSku(), product.getName())));
    }

    public ProductResponse getProductById(String productId) {
        return toResponse(findProductOrThrow(productId));
    }

    public ProductResponse getProductBySku(String sku) {
        Product product = productRepository.findBySku(sku)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "sku=" + sku));
        return toResponse(product);
    }

    public Page<ProductResponse> listProducts(Pageable pageable) {
        return productRepository.findByStatus(Product.ProductStatus.ACTIVE, pageable).map(this::toResponse);
    }

    public Page<ProductResponse> searchProducts(String query, Pageable pageable) {
        return productRepository.findByNameContainingIgnoreCaseOrBrandContainingIgnoreCase(query, query, pageable)
                .map(this::toResponse);
    }

    public Page<ProductResponse> listByCategory(String categoryId, Pageable pageable) {
        return productRepository.findByCategoryId(categoryId, pageable).map(this::toResponse);
    }

    // --- Inventory Operations ---

    public ProductResponse reserveStock(ReserveStockRequest request) {
        log.info("Reserving {} units of product {} for order {}", request.quantity(), request.productId(), request.orderId());
        Product product = findProductOrThrow(request.productId());

        if (product.getAvailableQuantity() < request.quantity()) {
            throw new BusinessRuleViolationException("INSUFFICIENT_STOCK",
                    "在庫不足です。利用可能在庫: " + product.getAvailableQuantity());
        }

        product.setReservedQuantity(product.getReservedQuantity() + request.quantity());
        product = productRepository.save(product);

        eventPublisher.publish(DomainEvent.create("InventoryReserved", "inventory-service",
                new InventoryEventPayload(product.getId(), product.getSku(), request.quantity(), product.getAvailableQuantity())));

        checkLowStock(product);
        return toResponse(product);
    }

    public ProductResponse stockIn(StockInRequest request) {
        log.info("Receiving {} units of product {}", request.quantity(), request.productId());
        Product product = findProductOrThrow(request.productId());

        product.setStockQuantity(product.getStockQuantity() + request.quantity());
        if (request.locationCode() != null) {
            product.setLocationCode(request.locationCode());
        }
        product = productRepository.save(product);

        eventPublisher.publish(DomainEvent.create("InventoryUpdated", "inventory-service",
                new InventoryEventPayload(product.getId(), product.getSku(), request.quantity(), product.getAvailableQuantity())));

        return toResponse(product);
    }

    public ProductResponse releaseStock(ReleaseStockRequest request) {
        log.info("Releasing {} units of product {} for order {}", request.quantity(), request.productId(), request.orderId());
        Product product = findProductOrThrow(request.productId());

        if (product.getReservedQuantity() < request.quantity()) {
            throw new BusinessRuleViolationException("RELEASE_EXCEEDS_RESERVED",
                    "解放数量が予約数量を超えています。予約数量: " + product.getReservedQuantity());
        }

        product.setReservedQuantity(product.getReservedQuantity() - request.quantity());
        product = productRepository.save(product);

        eventPublisher.publish(DomainEvent.create("InventoryUpdated", "inventory-service",
                new InventoryEventPayload(product.getId(), product.getSku(), request.quantity(), product.getAvailableQuantity())));

        return toResponse(product);
    }

    public ProductResponse stockOut(StockOutRequest request) {
        log.info("Stock out {} units of product {}: {}", request.quantity(), request.productId(), request.reason());
        Product product = findProductOrThrow(request.productId());

        if (product.getStockQuantity() < request.quantity()) {
            throw new BusinessRuleViolationException("INSUFFICIENT_STOCK_FOR_OUT",
                    "在庫数量が不足しています。現在の在庫: " + product.getStockQuantity());
        }

        product.setStockQuantity(product.getStockQuantity() - request.quantity());
        product = productRepository.save(product);

        eventPublisher.publish(DomainEvent.create("InventoryUpdated", "inventory-service",
                new InventoryEventPayload(product.getId(), product.getSku(), -request.quantity(), product.getAvailableQuantity())));

        checkLowStock(product);
        return toResponse(product);
    }

    public List<ProductResponse> getProductsByIds(List<String> productIds) {
        return productRepository.findAllById(productIds).stream()
                .map(this::toResponse)
                .toList();
    }

    public Page<ProductResponse> getLowStockProducts(int threshold, Pageable pageable) {
        return productRepository.findLowStockProducts(threshold, pageable)
                .map(this::toResponse);
    }

    // --- Price Operations ---

    public ProductResponse updatePrice(String productId, UpdatePriceRequest request) {
        log.info("Updating price for product: {}", productId);
        Product product = findProductOrThrow(productId);

        product.setRegularPrice(request.regularPrice());
        product.setSalePrice(request.salePrice());
        product.setSaleStartDate(request.saleStartDate());
        product.setSaleEndDate(request.saleEndDate());
        product = productRepository.save(product);

        eventPublisher.publish(DomainEvent.create("PriceUpdated", "inventory-service",
                new PriceEventPayload(product.getId(), product.getSku(), request.regularPrice(), request.salePrice())));

        return toResponse(product);
    }

    // --- Helpers ---

    private Product findProductOrThrow(String productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
    }

    private void checkLowStock(Product product) {
        if (product.getAvailableQuantity() <= LOW_STOCK_THRESHOLD && product.getAvailableQuantity() > 0) {
            eventPublisher.publish(DomainEvent.create("InventoryLow", "inventory-service",
                    new InventoryEventPayload(product.getId(), product.getSku(), 0, product.getAvailableQuantity())));
        } else if (product.getAvailableQuantity() <= 0) {
            eventPublisher.publish(DomainEvent.create("InventoryOutOfStock", "inventory-service",
                    new InventoryEventPayload(product.getId(), product.getSku(), 0, 0)));
        }
    }

    private ProductResponse toResponse(Product p) {
        return new ProductResponse(p.getId(), p.getSku(), p.getName(), p.getDescription(), p.getBrand(),
                p.getCategoryId(), p.getRegularPrice(), p.getSalePrice(), p.getCurrency(),
                p.getStockQuantity(), p.getAvailableQuantity(), p.getStatus().name(),
                p.getAttributes(), p.getTags(), p.getCreatedAt(), p.getUpdatedAt());
    }

    public record ProductEventPayload(String productId, String sku, String name) {}
    public record InventoryEventPayload(String productId, String sku, int quantity, int availableQuantity) {}
    public record PriceEventPayload(String productId, String sku, java.math.BigDecimal regularPrice, java.math.BigDecimal salePrice) {}
}
