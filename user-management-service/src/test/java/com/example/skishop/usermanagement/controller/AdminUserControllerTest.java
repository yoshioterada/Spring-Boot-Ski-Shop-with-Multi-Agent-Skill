package com.example.skishop.usermanagement.controller;

import com.example.skishop.usermanagement.config.SecurityConfig;
import com.example.skishop.usermanagement.dto.AssignRoleRequest;
import com.example.skishop.usermanagement.dto.UserResponse;
import com.example.skishop.usermanagement.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminUserController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "jwt.secret=test-secret-key-for-testing-only-minimum-256-bits-required-here-padding")
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    private final UUID userId = UUID.randomUUID();

    private UserResponse createUserResponse() {
        return new UserResponse(
                userId, "test@example.com", "Taro", "Yamada",
                "090-1234-5678", null, LocalDate.of(1990, 1, 1), "MALE",
                "ACTIVE", true, false, "USER",
                Instant.now(), Instant.now());
    }

    @Nested
    @DisplayName("GET /api/v1/admin/users")
    class ListUsers {

        @Test
        @DisplayName("ADMIN権限でユーザー一覧を取得できる")
        @WithMockUser(roles = "ADMIN")
        void should_returnUserList_when_admin() throws Exception {
            // Arrange
            Page<UserResponse> page = new PageImpl<>(List.of(createUserResponse()));
            when(userService.listUsers(any())).thenReturn(page);

            // Act & Assert
            mockMvc.perform(get("/api/v1/admin/users"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].email").value("test@example.com"));
        }

        @Test
        @DisplayName("一般ユーザーは403エラーとなる")
        @WithMockUser(roles = "USER")
        void should_returnForbidden_when_notAdmin() throws Exception {
            // Act & Assert
            mockMvc.perform(get("/api/v1/admin/users"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("未認証ユーザーは403エラーとなる")
        void should_returnForbidden_when_notAuthenticated() throws Exception {
            // Act & Assert
            mockMvc.perform(get("/api/v1/admin/users"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/admin/users/{id}/status")
    class UpdateStatus {

        @Test
        @DisplayName("ADMIN権限でユーザーステータスを更新できる")
        @WithMockUser(roles = "ADMIN")
        void should_updateStatus_when_admin() throws Exception {
            // Arrange
            doNothing().when(userService).updateUserStatus(userId, "SUSPENDED");

            // Act & Assert
            mockMvc.perform(put("/api/v1/admin/users/{id}/status", userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"SUSPENDED\"}"))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("一般ユーザーは403エラーとなる")
        @WithMockUser(roles = "USER")
        void should_returnForbidden_when_notAdmin() throws Exception {
            // Act & Assert
            mockMvc.perform(put("/api/v1/admin/users/{id}/status", userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"SUSPENDED\"}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/admin/users/{id}/roles")
    class AssignRole {

        @Test
        @DisplayName("ADMIN権限でロールを割り当てできる")
        @WithMockUser(roles = "ADMIN")
        void should_assignRole_when_admin() throws Exception {
            // Arrange
            var request = new AssignRoleRequest("MANAGER");
            var response = createUserResponse();
            when(userService.assignRole(eq(userId), eq("MANAGER"))).thenReturn(response);

            // Act & Assert
            mockMvc.perform(post("/api/v1/admin/users/{id}/roles", userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("test@example.com"));
        }

        @Test
        @DisplayName("一般ユーザーは403エラーとなる")
        @WithMockUser(roles = "USER")
        void should_returnForbidden_when_notAdmin() throws Exception {
            // Arrange
            var request = new AssignRoleRequest("MANAGER");

            // Act & Assert
            mockMvc.perform(post("/api/v1/admin/users/{id}/roles", userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("バリデーションエラーの場合、400エラーとなる")
        @WithMockUser(roles = "ADMIN")
        void should_returnBadRequest_when_roleNameBlank() throws Exception {
            // Arrange
            var request = new AssignRoleRequest("");

            // Act & Assert
            mockMvc.perform(post("/api/v1/admin/users/{id}/roles", userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }
}
