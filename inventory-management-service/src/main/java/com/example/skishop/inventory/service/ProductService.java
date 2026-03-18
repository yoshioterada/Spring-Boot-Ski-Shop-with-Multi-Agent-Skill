package com.example.skishop.inventory.service;

import com.example.skishop.inventory.dto.event.ProductCreatedEvent;
import com.example.skishop.inventory.dto.event.ProductUpdatedEvent;
import com.example.skishop.inventory.dto.request.CreateProductRequest;
import com.example.skishop.inventory.dto.request.UpdateProductRequest;
import com.example.skishop.inventory.dto.response.ProductDetailResponse;
import com.example.skishop.inventory.dto.response.ProductResponse;
import com.example.skishop.inventory.event.InventoryEventPublisher;
import com.example.skishop.inventory.model.Product;
import com.example.skishop.inventory.repository.CategoryRepository;
import com.example.skishop.inventory.repository.ProductRepository;
import com.example.skishop.inventory.repository.specification.ProductSpecification;
import com.example.skishop.shared.exception.ConflictException;
import com.example.skishop.shared.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 商品サービス。商品の CRUD・検索機能を提供する。
 */
@Service
@Transactional(readOnly = true)
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final InventoryEventPublisher eventPublisher;

    public ProductService(
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            InventoryEventPublisher eventPublisher) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 商品一覧を取得する（アクティブな商品のみ）。
     */
    public Page<ProductResponse> findAll(Pageable pageable) {
        return productRepository.findAll(ProductSpecification.isActive(), pageable)
                .map(ProductResponse::from);
    }

    /**
     * 商品を ID で取得する。
     */
    public ProductDetailResponse findById(Long id) {
        var product = productRepository.findByIdWithCategory(id)
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        return ProductDetailResponse.from(product);
    }

    /**
     * 商品を検索する（キーワード・カテゴリ・価格帯・ブランド）。
     */
    public Page<ProductResponse> search(
            String keyword, Long categoryId, BigDecimal minPrice, BigDecimal maxPrice,
            String brand, Pageable pageable) {
        Specification<Product> spec = ProductSpecification.buildSearchSpec(
                keyword, categoryId, minPrice, maxPrice, brand);
        return productRepository.findAll(spec, pageable).map(ProductResponse::from);
    }

    /**
     * 商品を登録する（管理者のみ）。
     */
    @Transactional
    public ProductDetailResponse create(CreateProductRequest request) {
        if (productRepository.existsBySku(request.sku())) {
            throw new ConflictException("DUPLICATE_SKU", "SKU が既に使用されています: " + request.sku());
        }

        var product = new Product();
        product.setSku(request.sku());
        product.setName(request.name());
        product.setDescription(request.description());
        product.setBrand(request.brand());
        product.setPrice(request.price());
        product.setCost(request.cost());
        product.setWeight(request.weight());
        product.setDimensions(request.dimensions());
        product.setIsActive(true);

        if (request.categoryId() != null) {
            var category = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category", request.categoryId()));
            product.setCategory(category);
        }

        var saved = productRepository.save(product);
        log.info("商品を登録しました: productId={}, sku={}", saved.getId(), saved.getSku());

        eventPublisher.publishProductCreated(new ProductCreatedEvent(
                saved.getId(),
                saved.getSku(),
                saved.getName(),
                saved.getBrand(),
                saved.getCategory() != null ? saved.getCategory().getId() : null,
                saved.getPrice(),
                OffsetDateTime.now()
        ));

        return ProductDetailResponse.from(saved);
    }

    /**
     * 商品を更新する（管理者のみ）。
     */
    @Transactional
    public ProductDetailResponse update(Long id, UpdateProductRequest request) {
        var product = productRepository.findByIdWithCategory(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));

        if (request.name() != null) {
            product.setName(request.name());
        }
        if (request.description() != null) {
            product.setDescription(request.description());
        }
        if (request.brand() != null) {
            product.setBrand(request.brand());
        }
        if (request.price() != null) {
            product.setPrice(request.price());
        }
        if (request.cost() != null) {
            product.setCost(request.cost());
        }
        if (request.weight() != null) {
            product.setWeight(request.weight());
        }
        if (request.dimensions() != null) {
            product.setDimensions(request.dimensions());
        }
        if (request.isActive() != null) {
            product.setIsActive(request.isActive());
        }
        if (request.categoryId() != null) {
            var category = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category", request.categoryId()));
            product.setCategory(category);
        }

        var saved = productRepository.save(product);
        log.info("商品を更新しました: productId={}", saved.getId());

        eventPublisher.publishProductUpdated(new ProductUpdatedEvent(
                saved.getId(),
                saved.getSku(),
                saved.getName(),
                saved.getPrice(),
                saved.getIsActive(),
                OffsetDateTime.now()
        ));

        return ProductDetailResponse.from(saved);
    }

    /**
     * 商品を論理削除する（管理者のみ）。
     */
    @Transactional
    public void delete(Long id) {
        var product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        product.setIsActive(false);
        productRepository.save(product);
        log.info("商品を削除しました: productId={}", id);
    }

    /**
     * カテゴリに属する商品一覧を取得する。
     */
    public Page<ProductResponse> findByCategory(Long categoryId, Pageable pageable) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new ResourceNotFoundException("Category", categoryId);
        }
        Specification<Product> spec = Specification.where(ProductSpecification.isActive())
                .and(ProductSpecification.hasCategory(categoryId));
        return productRepository.findAll(spec, pageable).map(ProductResponse::from);
    }
}
