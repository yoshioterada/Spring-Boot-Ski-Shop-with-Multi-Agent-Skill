package com.example.skishop.inventory.service;

import com.example.skishop.inventory.dto.request.CreateProductRequest;
import com.example.skishop.inventory.dto.request.UpdateProductRequest;
import com.example.skishop.inventory.event.InventoryEventPublisher;
import com.example.skishop.inventory.model.Category;
import com.example.skishop.inventory.model.Product;
import com.example.skishop.inventory.repository.CategoryRepository;
import com.example.skishop.inventory.repository.ProductRepository;
import com.example.skishop.inventory.repository.specification.ProductSpecification;
import com.example.skishop.shared.exception.ConflictException;
import com.example.skishop.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @InjectMocks
    private ProductService productService;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private InventoryEventPublisher eventPublisher;

    private Product sampleProduct;
    private Category sampleCategory;

    @BeforeEach
    void setUp() {
        sampleCategory = new Category();
        sampleCategory.setId(1L);
        sampleCategory.setName("スキー用品");
        sampleCategory.setIsActive(true);
        sampleCategory.setLevel(0);
        sampleCategory.setPath("/");

        sampleProduct = new Product();
        sampleProduct.setId(1L);
        sampleProduct.setSku("SKI-001");
        sampleProduct.setName("テストスキー板");
        sampleProduct.setPrice(new BigDecimal("50000.00"));
        sampleProduct.setIsActive(true);
        sampleProduct.setCategory(sampleCategory);
    }

    @Test
    @DisplayName("有効な ID が指定された場合、商品詳細を返す")
    void should_returnProductDetail_when_validIdProvided() {
        // Arrange
        when(productRepository.findByIdWithCategory(1L)).thenReturn(Optional.of(sampleProduct));

        // Act
        var result = productService.findById(1L);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.sku()).isEqualTo("SKI-001");
        assertThat(result.name()).isEqualTo("テストスキー板");
        verify(productRepository).findByIdWithCategory(1L);
    }

    @Test
    @DisplayName("存在しない ID が指定された場合、ResourceNotFoundException をスローする")
    void should_throwResourceNotFoundException_when_productNotFound() {
        // Arrange
        when(productRepository.findByIdWithCategory(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> productService.findById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Product")
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("非アクティブな商品 ID が指定された場合、ResourceNotFoundException をスローする")
    void should_throwResourceNotFoundException_when_productIsInactive() {
        // Arrange
        sampleProduct.setIsActive(false);
        when(productRepository.findByIdWithCategory(1L)).thenReturn(Optional.of(sampleProduct));

        // Act & Assert
        assertThatThrownBy(() -> productService.findById(1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("商品一覧取得時、アクティブな商品のページを返す")
    void should_returnActivePage_when_findAllCalled() {
        // Arrange
        var pageable = PageRequest.of(0, 20);
        var page = new PageImpl<>(List.of(sampleProduct));
        when(productRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        // Act
        var result = productService.findAll(pageable);

        // Assert
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).sku()).isEqualTo("SKI-001");
    }

    @Test
    @DisplayName("有効なリクエストで商品を登録した場合、商品詳細と ProductCreated イベントを返す")
    void should_createProductAndPublishEvent_when_validRequestProvided() {
        // Arrange
        var request = new CreateProductRequest(
                "SKI-002", "新商品", "説明", "ブランドA",
                1L, new BigDecimal("30000.00"), null, null, null
        );
        when(productRepository.existsBySku("SKI-002")).thenReturn(false);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(sampleCategory));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            var p = (Product) inv.getArgument(0);
            p.setId(2L);
            return p;
        });

        // Act
        var result = productService.create(request);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.sku()).isEqualTo("SKI-002");
        assertThat(result.name()).isEqualTo("新商品");
        verify(eventPublisher).publishProductCreated(any());
    }

    @Test
    @DisplayName("重複 SKU で商品を登録した場合、ConflictException をスローする")
    void should_throwConflictException_when_duplicateSkuProvided() {
        // Arrange
        var request = new CreateProductRequest(
                "SKI-001", "重複商品", null, null,
                null, new BigDecimal("10000.00"), null, null, null
        );
        when(productRepository.existsBySku("SKI-001")).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> productService.create(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("SKI-001");
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("存在しないカテゴリ ID で商品を登録した場合、ResourceNotFoundException をスローする")
    void should_throwResourceNotFoundException_when_categoryNotFound() {
        // Arrange
        var request = new CreateProductRequest(
                "SKI-003", "新商品", null, null,
                999L, new BigDecimal("10000.00"), null, null, null
        );
        when(productRepository.existsBySku("SKI-003")).thenReturn(false);
        when(categoryRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> productService.create(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category")
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("有効な更新リクエストで商品を更新した場合、更新された商品詳細を返す")
    void should_returnUpdatedProduct_when_validUpdateRequestProvided() {
        // Arrange
        var request = new UpdateProductRequest(
                "更新商品名", null, null, null,
                new BigDecimal("60000.00"), null, null, null, null
        );
        when(productRepository.findByIdWithCategory(1L)).thenReturn(Optional.of(sampleProduct));
        when(productRepository.save(any(Product.class))).thenReturn(sampleProduct);

        // Act
        var result = productService.update(1L, request);

        // Assert
        assertThat(result).isNotNull();
        verify(eventPublisher).publishProductUpdated(any());
    }

    @Test
    @DisplayName("存在しない ID で商品更新した場合、ResourceNotFoundException をスローする")
    void should_throwResourceNotFoundException_when_updatingNonExistentProduct() {
        // Arrange
        var request = new UpdateProductRequest("更新商品名", null, null, null, null, null, null, null, null);
        when(productRepository.findByIdWithCategory(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> productService.update(999L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Product");
    }

    @Test
    @DisplayName("存在する商品を削除した場合、商品が論理削除される")
    void should_logicallyDeleteProduct_when_deleteCalledWithValidId() {
        // Arrange
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
        when(productRepository.save(any(Product.class))).thenReturn(sampleProduct);

        // Act
        productService.delete(1L);

        // Assert
        var captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().getIsActive()).isFalse();
    }

    @Test
    @DisplayName("存在しない ID で商品削除した場合、ResourceNotFoundException をスローする")
    void should_throwResourceNotFoundException_when_deletingNonExistentProduct() {
        // Arrange
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> productService.delete(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Product");
    }

    @Test
    @DisplayName("存在しないカテゴリ ID でカテゴリ別商品一覧を取得した場合、ResourceNotFoundException をスローする")
    void should_throwResourceNotFoundException_when_categoryNotFoundForProductList() {
        // Arrange
        var pageable = PageRequest.of(0, 20);
        when(categoryRepository.existsById(999L)).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> productService.findByCategory(999L, pageable))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category");
    }

    @Test
    @DisplayName("カテゴリ別商品一覧を取得した場合、そのカテゴリの商品ページを返す")
    void should_returnProductsByCategory_when_validCategoryId() {
        // Arrange
        var pageable = PageRequest.of(0, 20);
        var page = new PageImpl<>(List.of(sampleProduct));
        when(categoryRepository.existsById(1L)).thenReturn(true);
        when(productRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        // Act
        var result = productService.findByCategory(1L, pageable);

        // Assert
        assertThat(result.getContent()).hasSize(1);
    }
}
