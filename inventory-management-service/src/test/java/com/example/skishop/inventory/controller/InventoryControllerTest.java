package com.example.skishop.inventory.controller;

import com.example.skishop.inventory.dto.response.InventoryResponse;
import com.example.skishop.inventory.dto.request.UpdateInventoryRequest;
import com.example.skishop.inventory.service.InventoryService;
import com.example.skishop.shared.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InventoryController.class)
@Import(com.example.skishop.inventory.config.SecurityConfig.class)
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private InventoryService inventoryService;

    private InventoryResponse sampleInventoryResponse() {
        return new InventoryResponse(
                1L, 1L, "テストスキー板", "SKI-001",
                100, 10, 90, "MAIN", 20, false,
                OffsetDateTime.now()
        );
    }

    @Test
    @DisplayName("商品在庫確認 - 認証不要で成功する")
    void should_return200_when_getProductInventoryWithoutAuth() throws Exception {
        // Arrange
        when(inventoryService.findByProductId(1L)).thenReturn(List.of(sampleInventoryResponse()));

        // Act & Assert
        mockMvc.perform(get("/api/v1/products/1/inventory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productId").value(1))
                .andExpect(jsonPath("$[0].stockQuantity").value(100));
    }

    @Test
    @DisplayName("商品在庫確認 - 存在しない商品 ID で 404 を返す")
    void should_return404_when_getInventoryForNonExistentProduct() throws Exception {
        // Arrange
        when(inventoryService.findByProductId(999L))
                .thenThrow(new ResourceNotFoundException("Product", 999L));

        // Act & Assert
        mockMvc.perform(get("/api/v1/products/999/inventory"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("在庫不足商品一覧 - 認証なしで 401/403 を返す")
    void should_return401or403_when_getLowStockWithoutAuth() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/v1/inventory/low-stock"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("在庫不足商品一覧 - 管理者権限で成功する")
    @WithMockUser(roles = {"INVENTORY_MANAGER"})
    void should_return200_when_getLowStockWithAdminRole() throws Exception {
        // Arrange
        when(inventoryService.findLowStockInventories()).thenReturn(List.of(sampleInventoryResponse()));

        // Act & Assert
        mockMvc.perform(get("/api/v1/inventory/low-stock"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("在庫更新 - 管理者権限で成功する")
    @WithMockUser(roles = {"INVENTORY_MANAGER"})
    void should_return200_when_updateInventoryWithAdminRole() throws Exception {
        // Arrange
        var request = new UpdateInventoryRequest(80, 5, "MAIN", 20);
        when(inventoryService.updateInventory(anyLong(), any(UpdateInventoryRequest.class)))
                .thenReturn(sampleInventoryResponse());

        // Act & Assert
        mockMvc.perform(put("/api/v1/products/1/inventory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("在庫更新 - バリデーションエラーで 400 を返す")
    @WithMockUser(roles = {"INVENTORY_MANAGER"})
    void should_return400_when_updateInventoryWithInvalidData() throws Exception {
        // Arrange: stockQuantity が null (必須)
        var invalidRequest = new UpdateInventoryRequest(null, null, "MAIN", null);

        // Act & Assert
        mockMvc.perform(put("/api/v1/products/1/inventory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }
}
