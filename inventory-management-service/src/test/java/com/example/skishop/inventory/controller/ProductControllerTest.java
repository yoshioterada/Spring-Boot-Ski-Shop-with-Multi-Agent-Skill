package com.example.skishop.inventory.controller;

import com.example.skishop.inventory.dto.request.CreateProductRequest;
import com.example.skishop.inventory.dto.request.UpdateProductRequest;
import com.example.skishop.inventory.dto.response.ProductDetailResponse;
import com.example.skishop.inventory.dto.response.ProductResponse;
import com.example.skishop.inventory.service.ProductService;
import com.example.skishop.shared.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@Import(com.example.skishop.inventory.config.SecurityConfig.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProductService productService;

    private ProductResponse sampleProductResponse() {
        return new ProductResponse(
                1L, "SKI-001", "テストスキー板", "説明", "ブランドA",
                1L, "スキー用品", new BigDecimal("50000.00"), true, null,
                OffsetDateTime.now(), OffsetDateTime.now()
        );
    }

    private ProductDetailResponse sampleProductDetailResponse() {
        return new ProductDetailResponse(
                1L, "SKI-001", "テストスキー板", "説明", "ブランドA",
                1L, "スキー用品", new BigDecimal("50000.00"), new BigDecimal("30000.00"),
                null, null, true, List.of(), List.of(),
                OffsetDateTime.now(), OffsetDateTime.now()
        );
    }

    @Test
    @DisplayName("商品一覧取得 - 認証不要で成功する")
    void should_return200_when_listProductsCalledWithoutAuth() throws Exception {
        // Arrange
        var page = new PageImpl<>(List.of(sampleProductResponse()));
        when(productService.findAll(any(Pageable.class))).thenReturn(page);

        // Act & Assert
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sku").value("SKI-001"));
    }

    @Test
    @DisplayName("商品詳細取得 - 有効な ID で成功する")
    void should_return200_when_getProductWithValidId() throws Exception {
        // Arrange
        when(productService.findById(1L)).thenReturn(sampleProductDetailResponse());

        // Act & Assert
        mockMvc.perform(get("/api/v1/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.sku").value("SKI-001"));
    }

    @Test
    @DisplayName("商品詳細取得 - 存在しない ID で 404 を返す")
    void should_return404_when_getProductWithNonExistentId() throws Exception {
        // Arrange
        when(productService.findById(999L)).thenThrow(new ResourceNotFoundException("Product", 999L));

        // Act & Assert
        mockMvc.perform(get("/api/v1/products/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("商品検索 - 認証不要で成功する")
    void should_return200_when_searchProductsCalledWithoutAuth() throws Exception {
        // Arrange
        var page = new PageImpl<>(List.of(sampleProductResponse()));
        when(productService.search(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(page);

        // Act & Assert
        mockMvc.perform(get("/api/v1/products/search").param("keyword", "スキー"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("商品登録 - 認証なしで 401/403 を返す")
    void should_return401or403_when_createProductWithoutAuth() throws Exception {
        // Arrange
        var request = new CreateProductRequest(
                "SKI-002", "新商品", null, null,
                null, new BigDecimal("10000.00"), null, null, null
        );

        // Act & Assert
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("商品登録 - 管理者権限で成功する")
    @WithMockUser(roles = {"INVENTORY_MANAGER"})
    void should_return201_when_createProductWithAdminRole() throws Exception {
        // Arrange
        var request = new CreateProductRequest(
                "SKI-002", "新商品", null, null,
                null, new BigDecimal("10000.00"), null, null, null
        );
        when(productService.create(any(CreateProductRequest.class)))
                .thenReturn(sampleProductDetailResponse());

        // Act & Assert
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("商品登録 - バリデーションエラーで 400 を返す")
    @WithMockUser(roles = {"INVENTORY_MANAGER"})
    void should_return400_when_createProductWithInvalidData() throws Exception {
        // Arrange
        var invalidRequest = new CreateProductRequest(
                "", "", null, null,
                null, null, null, null, null
        );

        // Act & Assert
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("商品更新 - 管理者権限で成功する")
    @WithMockUser(roles = {"INVENTORY_MANAGER"})
    void should_return200_when_updateProductWithAdminRole() throws Exception {
        // Arrange
        var request = new UpdateProductRequest("更新商品名", null, null, null, null, null, null, null, null);
        when(productService.update(anyLong(), any(UpdateProductRequest.class)))
                .thenReturn(sampleProductDetailResponse());

        // Act & Assert
        mockMvc.perform(put("/api/v1/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("商品削除 - 管理者権限で成功する")
    @WithMockUser(roles = {"INVENTORY_MANAGER"})
    void should_return204_when_deleteProductWithAdminRole() throws Exception {
        // Arrange
        doNothing().when(productService).delete(1L);

        // Act & Assert
        mockMvc.perform(delete("/api/v1/products/1").with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("商品削除 - 存在しない ID で 404 を返す")
    @WithMockUser(roles = {"INVENTORY_MANAGER"})
    void should_return404_when_deleteNonExistentProduct() throws Exception {
        // Arrange
        doThrow(new ResourceNotFoundException("Product", 999L)).when(productService).delete(999L);

        // Act & Assert
        mockMvc.perform(delete("/api/v1/products/999").with(csrf()))
                .andExpect(status().isNotFound());
    }
}
