package com.example.skishop.usermanagement.controller;

import com.example.skishop.usermanagement.config.SecurityConfig;
import com.example.skishop.usermanagement.dto.UpdateUserRequest;
import com.example.skishop.usermanagement.dto.UserResponse;
import com.example.skishop.usermanagement.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "jwt.secret=test-secret-key-for-testing-only-minimum-256-bits-required-here-padding")
class UserControllerIdorTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();

    private UsernamePasswordAuthenticationToken ownerAuth() {
        return new UsernamePasswordAuthenticationToken(
                ownerId, null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    }

    private UsernamePasswordAuthenticationToken adminAuth() {
        return new UsernamePasswordAuthenticationToken(
                UUID.randomUUID(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private UserResponse createUserResponse(UUID id) {
        return new UserResponse(
                id, "test@example.com", "Taro", "Yamada",
                "090-1234-5678", LocalDate.of(1990, 1, 1), "MALE",
                "ACTIVE", true, false, "CUSTOMER",
                Instant.now(), Instant.now());
    }

    @Nested
    @DisplayName("GET /api/v1/users/{id} — IDOR防止")
    class GetUser {

        @Test
        @DisplayName("自分のユーザー情報を取得できる")
        void should_allowAccess_when_ownerAccesses() throws Exception {
            when(userService.getUserById(ownerId)).thenReturn(createUserResponse(ownerId));

            mockMvc.perform(get("/api/v1/users/{id}", ownerId)
                            .with(authentication(ownerAuth())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(ownerId.toString()));
        }

        @Test
        @DisplayName("他人のユーザー情報は取得できない（403）")
        void should_denyAccess_when_otherUserAccesses() throws Exception {
            mockMvc.perform(get("/api/v1/users/{id}", otherUserId)
                            .with(authentication(ownerAuth())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("ADMIN権限で他人のユーザー情報を取得できる")
        void should_allowAccess_when_adminAccesses() throws Exception {
            when(userService.getUserById(otherUserId)).thenReturn(createUserResponse(otherUserId));

            mockMvc.perform(get("/api/v1/users/{id}", otherUserId)
                            .with(authentication(adminAuth())))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/users/{id} — IDOR防止")
    class UpdateUser {

        @Test
        @DisplayName("自分のユーザー情報を更新できる")
        void should_allowUpdate_when_ownerUpdates() throws Exception {
            var request = new UpdateUserRequest("Jiro", "Suzuki", null, null, null);
            when(userService.updateUser(ownerId, request)).thenReturn(createUserResponse(ownerId));

            mockMvc.perform(put("/api/v1/users/{id}", ownerId)
                            .with(authentication(ownerAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("他人のユーザー情報は更新できない（403）")
        void should_denyUpdate_when_otherUserUpdates() throws Exception {
            var request = new UpdateUserRequest("Jiro", "Suzuki", null, null, null);

            mockMvc.perform(put("/api/v1/users/{id}", otherUserId)
                            .with(authentication(ownerAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/users/{id} — IDOR防止")
    class DeleteUser {

        @Test
        @DisplayName("他人のアカウントは削除できない（403）")
        void should_denyDelete_when_otherUserDeletes() throws Exception {
            mockMvc.perform(delete("/api/v1/users/{id}", otherUserId)
                            .with(authentication(ownerAuth())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("ADMIN権限で他人のアカウントを削除できる")
        void should_allowDelete_when_adminDeletes() throws Exception {
            mockMvc.perform(delete("/api/v1/users/{id}", otherUserId)
                            .with(authentication(adminAuth())))
                    .andExpect(status().isNoContent());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/users/{id}/password — IDOR防止")
    class ChangePassword {

        @Test
        @DisplayName("他人のパスワードは変更できない（403）")
        void should_denyPasswordChange_when_otherUserChanges() throws Exception {
            mockMvc.perform(put("/api/v1/users/{id}/password", otherUserId)
                            .with(authentication(ownerAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"currentPassword": "old", "newPassword": "newPass123!"}
                                    """))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/users/{id}/preferences — IDOR防止")
    class Preferences {

        @Test
        @DisplayName("他人の設定は取得できない（403）")
        void should_denyPreferencesAccess_when_otherUser() throws Exception {
            mockMvc.perform(get("/api/v1/users/{id}/preferences", otherUserId)
                            .with(authentication(ownerAuth())))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("未認証アクセス")
    class Unauthenticated {

        @Test
        @DisplayName("未認証ユーザーはアクセスできない（403）")
        void should_denyAccess_when_unauthenticated() throws Exception {
            mockMvc.perform(get("/api/v1/users/{id}", ownerId))
                    .andExpect(status().isForbidden());
        }
    }
}
