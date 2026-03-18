package com.example.skishop.usermanagement.controller;

import com.example.skishop.usermanagement.config.SecurityConfig;
import com.example.skishop.usermanagement.dto.request.AssignRoleRequest;
import com.example.skishop.usermanagement.dto.response.AdminUserResponse;
import com.example.skishop.usermanagement.exception.GlobalExceptionHandler;
import com.example.skishop.usermanagement.exception.ResourceNotFoundException;
import com.example.skishop.usermanagement.model.UserStatus;
import com.example.skishop.usermanagement.service.AdminUserService;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AdminUserController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AdminUserService adminUserService;

    private AdminUserResponse createAdminUserResponse() {
        return new AdminUserResponse(
            UUID.randomUUID(),
            "admin@example.com",
            "管理者",
            "テスト",
            null, null,
            OffsetDateTime.now(),
            OffsetDateTime.now(),
            null,
            UserStatus.ACTIVE,
            List.of("STORE_ADMIN")
        );
    }

    @Test
    @DisplayName("権限なしでユーザー一覧にアクセスした場合、403を返す")
    @WithMockUser(roles = "CUSTOMER")
    void should_return403_when_userWithoutAdminRole() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/admin/users"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("STORE_ADMINロールでユーザー一覧にアクセスした場合、200を返す")
    @WithMockUser(roles = "STORE_ADMIN")
    void should_return200_when_storeAdminAccessesUserList() throws Exception {
        // Arrange
        when(adminUserService.listUsers(any(Pageable.class), any()))
            .thenReturn(new PageImpl<>(List.of(createAdminUserResponse())));

        // Act & Assert
        mockMvc.perform(get("/api/admin/users"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content[0].email").value("admin@example.com"));
    }

    @Test
    @DisplayName("SYSTEM_ADMINロールでユーザー削除にアクセスした場合、204を返す")
    @WithMockUser(roles = "SYSTEM_ADMIN")
    void should_return204_when_systemAdminDeletesUser() throws Exception {
        // Arrange
        UUID userId = UUID.randomUUID();

        // Act & Assert
        mockMvc.perform(delete("/api/admin/users/" + userId)
                .with(csrf()))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("STORE_ADMINがユーザー削除にアクセスした場合、403を返す")
    @WithMockUser(roles = "STORE_ADMIN")
    void should_return403_when_storeAdminTriesToDeleteUser() throws Exception {
        // Arrange
        UUID userId = UUID.randomUUID();

        // Act & Assert
        mockMvc.perform(delete("/api/admin/users/" + userId)
                .with(csrf()))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("存在しないユーザーIDでアクセスした場合、404を返す")
    @WithMockUser(roles = "STORE_ADMIN")
    void should_return404_when_userNotFound() throws Exception {
        // Arrange
        UUID unknownId = UUID.randomUUID();
        when(adminUserService.getUserById(unknownId))
            .thenThrow(new ResourceNotFoundException("User", unknownId));

        // Act & Assert
        mockMvc.perform(get("/api/admin/users/" + unknownId))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("SYSTEM_ADMINがロール付与にアクセスした場合、200を返す")
    @WithMockUser(roles = "SYSTEM_ADMIN")
    void should_return200_when_systemAdminAssignsRole() throws Exception {
        // Arrange
        UUID userId = UUID.randomUUID();
        AssignRoleRequest request = new AssignRoleRequest(1L);
        when(adminUserService.assignRole(any(UUID.class), any(Long.class)))
            .thenReturn(createAdminUserResponse());

        // Act & Assert
        mockMvc.perform(post("/api/admin/users/" + userId + "/roles")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(csrf()))
            .andExpect(status().isOk());
    }
}
