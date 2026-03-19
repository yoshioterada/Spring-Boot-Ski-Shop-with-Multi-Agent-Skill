package com.example.skishop.inventory.service;

import com.example.skishop.common.event.EventPublisher;
import com.example.skishop.common.exception.BusinessRuleViolationException;
import com.example.skishop.common.exception.ResourceNotFoundException;
import com.example.skishop.inventory.dto.*;
import com.example.skishop.inventory.model.Product;
import com.example.skishop.inventory.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private EventPublisher eventPublisher;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, eventPublisher);
    }

    @Nested
    @DisplayName("createProduct - 商品作成")
    class CreateProductTests {

        @Test
        @DisplayName("有効な情報で商品作成が成功する")
        void should_createProduct_when_validRequest() {
            // Arrange
            var request = new CreateProductRequest("SKI-001", "Salomon X-Drive", "テスト説明",
                    "Salomon", "cat-1", BigDecimal.valueOf(89800), 50, "WAREHOUSE-A", null, List.of("ski", "alpine"));
            when(productRepository.existsBySku("SKI-001")).thenReturn(false);
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            // Act
            ProductResponse response = productService.createProduct(request);

            // Assert
            assertThat(response.sku()).isEqualTo("SKI-001");
            assertThat(response.name()).isEqualTo("Salomon X-Drive");
            verify(eventPublisher).publish(any());
        }

        @Test
        @DisplayName("重複SKUで商品作成が失敗する")
        void should_throwException_when_skuExists() {
            // Arrange
            var request = new CreateProductRequest("SKI-001", "Duplicate", null,
                    "Brand", null, BigDecimal.valueOf(10000), 10, null, null, null);
            when(productRepository.existsBySku("SKI-001")).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> productService.createProduct(request))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("SKU");
        }
    }

    @Nested
    @DisplayName("getProduct - 商品取得")
    class GetProductTests {

        @Test
        @DisplayName("存在しない商品でResourceNotFoundExceptionがスローされる")
        void should_throwNotFound_when_productNotExists() {
            when(productRepository.findById("nonexistent")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.getProductById("nonexistent"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("SKUで商品取得が成功する")
        void should_returnProduct_when_validSku() {
            var product = new Product("SKI-001", "Test Product", "Brand", "cat-1");
            when(productRepository.findBySku("SKI-001")).thenReturn(Optional.of(product));

            ProductResponse response = productService.getProductBySku("SKI-001");

            assertThat(response.sku()).isEqualTo("SKI-001");
        }

        @Test
        @DisplayName("商品一覧取得が成功する")
        void should_returnPagedProducts_when_listProducts() {
            var product = new Product("SKI-001", "Test Product", "Brand", "cat-1");
            Pageable pageable = PageRequest.of(0, 20);
            Page<Product> page = new PageImpl<>(List.of(product), pageable, 1);
            when(productRepository.findByStatus(Product.ProductStatus.ACTIVE, pageable)).thenReturn(page);

            Page<ProductResponse> result = productService.listProducts(pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().getFirst().sku()).isEqualTo("SKI-001");
        }
    }

    @Nested
    @DisplayName("reserveStock - 在庫予約")
    class ReserveStockTests {

        @Test
        @DisplayName("在庫予約が成功する")
        void should_reserveStock_when_sufficientInventory() {
            var product = new Product("SKI-001", "Test Product", "Brand", "cat-1");
            product.setStockQuantity(100);
            product.setReservedQuantity(10);
            when(productRepository.findById("prod-1")).thenReturn(Optional.of(product));
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            productService.reserveStock(new ReserveStockRequest("prod-1", 20, "order-1"));

            assertThat(product.getReservedQuantity()).isEqualTo(30);
            verify(eventPublisher, atLeastOnce()).publish(any());
        }

        @Test
        @DisplayName("在庫不足で予約が失敗する")
        void should_throwException_when_insufficientStock() {
            var product = new Product("SKI-001", "Test Product", "Brand", "cat-1");
            product.setStockQuantity(5);
            product.setReservedQuantity(3);
            when(productRepository.findById("prod-1")).thenReturn(Optional.of(product));

            assertThatThrownBy(() -> productService.reserveStock(new ReserveStockRequest("prod-1", 10, "order-1")))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("在庫不足");
        }
    }

    @Nested
    @DisplayName("releaseStock - 在庫予約解放")
    class ReleaseStockTests {

        @Test
        @DisplayName("在庫予約解放が成功する")
        void should_releaseStock_when_validRequest() {
            var product = new Product("SKI-001", "Test Product", "Brand", "cat-1");
            product.setStockQuantity(100);
            product.setReservedQuantity(30);
            when(productRepository.findById("prod-1")).thenReturn(Optional.of(product));
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            ProductResponse response = productService.releaseStock(new ReleaseStockRequest("prod-1", 10, "order-1"));

            assertThat(product.getReservedQuantity()).isEqualTo(20);
            verify(eventPublisher).publish(any());
        }

        @Test
        @DisplayName("予約数量を超える解放で失敗する")
        void should_throwException_when_releaseExceedsReserved() {
            var product = new Product("SKI-001", "Test Product", "Brand", "cat-1");
            product.setStockQuantity(100);
            product.setReservedQuantity(5);
            when(productRepository.findById("prod-1")).thenReturn(Optional.of(product));

            assertThatThrownBy(() -> productService.releaseStock(new ReleaseStockRequest("prod-1", 10, "order-1")))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("予約数量");
        }
    }

    @Nested
    @DisplayName("stockIn/stockOut - 入出庫")
    class StockInOutTests {

        @Test
        @DisplayName("入荷処理が成功する")
        void should_stockIn_when_validRequest() {
            var product = new Product("SKI-001", "Test Product", "Brand", "cat-1");
            product.setStockQuantity(50);
            when(productRepository.findById("prod-1")).thenReturn(Optional.of(product));
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            productService.stockIn(new StockInRequest("prod-1", 30, "WAREHOUSE-B"));

            assertThat(product.getStockQuantity()).isEqualTo(80);
            assertThat(product.getLocationCode()).isEqualTo("WAREHOUSE-B");
        }

        @Test
        @DisplayName("出庫処理が成功する")
        void should_stockOut_when_sufficientQuantity() {
            var product = new Product("SKI-001", "Test Product", "Brand", "cat-1");
            product.setStockQuantity(50);
            when(productRepository.findById("prod-1")).thenReturn(Optional.of(product));
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            ProductResponse response = productService.stockOut(new StockOutRequest("prod-1", 20, "damaged"));

            assertThat(product.getStockQuantity()).isEqualTo(30);
        }

        @Test
        @DisplayName("在庫不足で出庫処理が失敗する")
        void should_throwException_when_insufficientStockForOut() {
            var product = new Product("SKI-001", "Test Product", "Brand", "cat-1");
            product.setStockQuantity(5);
            when(productRepository.findById("prod-1")).thenReturn(Optional.of(product));

            assertThatThrownBy(() -> productService.stockOut(new StockOutRequest("prod-1", 10, "reason")))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("在庫数量");
        }
    }

    @Nested
    @DisplayName("updatePrice - 価格更新")
    class UpdatePriceTests {

        @Test
        @DisplayName("価格更新が成功する")
        void should_updatePrice_when_validRequest() {
            var product = new Product("SKI-001", "Test Product", "Brand", "cat-1");
            product.setRegularPrice(BigDecimal.valueOf(89800));
            var request = new UpdatePriceRequest(BigDecimal.valueOf(79800), BigDecimal.valueOf(69800),
                    Instant.now(), Instant.now().plusSeconds(86400));
            when(productRepository.findById("prod-1")).thenReturn(Optional.of(product));
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            ProductResponse response = productService.updatePrice("prod-1", request);

            assertThat(response.regularPrice()).isEqualByComparingTo(BigDecimal.valueOf(79800));
            assertThat(response.salePrice()).isEqualByComparingTo(BigDecimal.valueOf(69800));
            verify(eventPublisher).publish(any());
        }
    }

    @Nested
    @DisplayName("updateProduct - 商品更新")
    class UpdateProductTests {

        @Test
        @DisplayName("有効なリクエストで商品更新が成功する")
        void should_updateProduct_when_validRequest() {
            // Arrange
            var product = new Product("SKI-001", "Old Name", "Old Brand", "cat-1");
            product.setDescription("Old Description");
            var request = new UpdateProductRequest("New Name", "New Description", "New Brand",
                    "cat-2", BigDecimal.valueOf(59800), null, List.of("updated"));
            when(productRepository.findById("prod-1")).thenReturn(Optional.of(product));
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            // Act
            ProductResponse response = productService.updateProduct("prod-1", request);

            // Assert
            assertThat(response.name()).isEqualTo("New Name");
            assertThat(response.description()).isEqualTo("New Description");
            assertThat(response.brand()).isEqualTo("New Brand");
            assertThat(response.categoryId()).isEqualTo("cat-2");
            assertThat(response.regularPrice()).isEqualByComparingTo(BigDecimal.valueOf(59800));
            assertThat(response.tags()).containsExactly("updated");
            verify(eventPublisher).publish(any());
        }

        @Test
        @DisplayName("nullフィールドは既存値を保持する")
        void should_keepExistingValues_when_fieldsAreNull() {
            // Arrange
            var product = new Product("SKI-001", "Original Name", "Original Brand", "cat-1");
            product.setDescription("Original Description");
            product.setRegularPrice(BigDecimal.valueOf(89800));
            var request = new UpdateProductRequest(null, null, null, null, null, null, null);
            when(productRepository.findById("prod-1")).thenReturn(Optional.of(product));
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            // Act
            ProductResponse response = productService.updateProduct("prod-1", request);

            // Assert
            assertThat(response.name()).isEqualTo("Original Name");
            assertThat(response.description()).isEqualTo("Original Description");
            assertThat(response.brand()).isEqualTo("Original Brand");
            assertThat(response.regularPrice()).isEqualByComparingTo(BigDecimal.valueOf(89800));
        }

        @Test
        @DisplayName("存在しない商品の更新でResourceNotFoundExceptionがスローされる")
        void should_throwNotFound_when_productNotExists() {
            // Arrange
            when(productRepository.findById("nonexistent")).thenReturn(Optional.empty());
            var request = new UpdateProductRequest("Name", null, null, null, null, null, null);

            // Act & Assert
            assertThatThrownBy(() -> productService.updateProduct("nonexistent", request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("deleteProduct - 商品削除（ソフトデリート）")
    class DeleteProductTests {

        @Test
        @DisplayName("予約なしの商品のソフトデリートが成功する")
        void should_softDeleteProduct_when_noReservations() {
            // Arrange
            var product = new Product("SKI-001", "Test Product", "Brand", "cat-1");
            product.setReservedQuantity(0);
            when(productRepository.findById("prod-1")).thenReturn(Optional.of(product));
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            // Act
            productService.deleteProduct("prod-1");

            // Assert
            assertThat(product.getStatus()).isEqualTo(Product.ProductStatus.DISCONTINUED);
            verify(productRepository).save(product);
            verify(eventPublisher).publish(any());
        }

        @Test
        @DisplayName("予約中の在庫がある場合に削除が失敗する")
        void should_throwException_when_productHasReservations() {
            // Arrange
            var product = new Product("SKI-001", "Test Product", "Brand", "cat-1");
            product.setReservedQuantity(5);
            when(productRepository.findById("prod-1")).thenReturn(Optional.of(product));

            // Act & Assert
            assertThatThrownBy(() -> productService.deleteProduct("prod-1"))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("予約中の在庫");
        }

        @Test
        @DisplayName("存在しない商品の削除でResourceNotFoundExceptionがスローされる")
        void should_throwNotFound_when_deletingNonExistentProduct() {
            // Arrange
            when(productRepository.findById("nonexistent")).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> productService.deleteProduct("nonexistent"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("batch/lowStock - バッチ/低在庫")
    class BatchAndLowStockTests {

        @Test
        @DisplayName("IDリストで複数商品取得が成功する")
        void should_returnProducts_when_batchGet() {
            var p1 = new Product("SKI-001", "Product 1", "Brand", "cat-1");
            var p2 = new Product("SKI-002", "Product 2", "Brand", "cat-1");
            when(productRepository.findAllById(List.of("id-1", "id-2"))).thenReturn(List.of(p1, p2));

            List<ProductResponse> result = productService.getProductsByIds(List.of("id-1", "id-2"));

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("低在庫商品一覧が取得できる")
        void should_returnLowStockProducts() {
            var lowStock = new Product("SKI-001", "Low Stock", "Brand", "cat-1");
            lowStock.setStockQuantity(5);

            Page<Product> page = new PageImpl<>(List.of(lowStock));
            when(productRepository.findLowStockProducts(eq(10), any(Pageable.class)))
                    .thenReturn(page);

            Page<ProductResponse> result = productService.getLowStockProducts(10, PageRequest.of(0, 20));

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().getFirst().sku()).isEqualTo("SKI-001");
        }
    }
}
