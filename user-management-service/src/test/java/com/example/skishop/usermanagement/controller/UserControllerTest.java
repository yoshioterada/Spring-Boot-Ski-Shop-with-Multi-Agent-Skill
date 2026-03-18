package com.example.skishop.usermanagement.controller;

import com.example.skishop.usermanagement.config.SecurityConfig;
import com.example.skishop.usermanagement.dto.request.UpdateProfileRequest;
import com.example.skishop.usermanagement.dto.response.UserProfileResponse;
import com.example.skishop.usermanagement.exception.GlobalExceptionHandler;
import com.example.skishop.usermanagement.exception.ResourceNotFoundException;
import com.example.skishop.usermanagement.model.UserStatus;
import com.example.skishop.usermanagement.service.AddressService;
import com.example.skishop.usermanagement.service.UserPreferenceService;
import com.example.skishop.usermanagement.service.UserService;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = UserController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private AddressService addressService;

    @MockitoBean
    private UserPreferenceService userPreferenceService;

    @Test
    @DisplayName("認証されていない場合、/me へのアクセスは401を返す")
    void should_return401_when_notAuthenticated() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/v1/users/me"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("認証済みユーザーが/meにアクセスした場合、200とプロフィールを返す")
    @WithMockUser(username = "550e8400-e29b-41d4-a716-446655440000")
    void should_return200WithProfile_when_authenticated() throws Exception {
        // Arrange
        UUID userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        UserProfileResponse response = new UserProfileResponse(
            userId, "test@example.com", "太郎", "山田",
            null, null, OffsetDateTime.now(),
            UserStatus.ACTIVE,
            List.of("CUSTOMER")
        );
        when(userService.getCurrentUserProfile(any())).thenReturn(response);

        // Act & Assert
        mockMvc.perform(get("/api/v1/users/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("test@example.com"));
    }

    @Test
    @DisplayName("無効なリクエストボディでプロフィールを更新しようとした場合、400を返す")
    @WithMockUser(username = "550e8400-e29b-41d4-a716-446655440000")
    void should_return400_when_invalidUpdateProfileRequest() throws Exception {
        // Arrange - empty firstName and lastName
        String invalidJson = """
            {
                "firstName": "",
                "lastName": ""
            }
            """;

        // Act & Assert
        mockMvc.perform(put("/api/v1/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson)
                .with(csrf()))
            .andExpect(status().isBadRequest());
    }
}
