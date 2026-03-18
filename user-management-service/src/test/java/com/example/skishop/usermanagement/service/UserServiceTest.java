package com.example.skishop.usermanagement.service;

import com.example.skishop.usermanagement.dto.request.UpdateProfileRequest;
import com.example.skishop.usermanagement.dto.response.UserProfileResponse;
import com.example.skishop.usermanagement.event.UserEventProducer;
import com.example.skishop.usermanagement.event.UserUpdatedEvent;
import com.example.skishop.usermanagement.exception.ResourceNotFoundException;
import com.example.skishop.usermanagement.model.Role;
import com.example.skishop.usermanagement.model.RoleName;
import com.example.skishop.usermanagement.model.User;
import com.example.skishop.usermanagement.model.UserStatus;
import com.example.skishop.usermanagement.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserEventProducer userEventProducer;

    private User testUser;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testUser = new User();
        testUser.setId(testUserId);
        testUser.setEmail("test@example.com");
        testUser.setPasswordHash("hashedPassword");
        testUser.setFirstName("太郎");
        testUser.setLastName("山田");
        testUser.setStatus(UserStatus.ACTIVE);
        testUser.setCreatedAt(OffsetDateTime.now());
        testUser.setUpdatedAt(OffsetDateTime.now());
    }

    @Test
    @DisplayName("有効なIDが指定された場合、ユーザープロフィールを返す")
    void should_returnUserProfile_when_validIdProvided() {
        // Arrange
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));

        // Act
        UserProfileResponse result = userService.getCurrentUserProfile(testUserId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(testUserId);
        assertThat(result.email()).isEqualTo("test@example.com");
        assertThat(result.firstName()).isEqualTo("太郎");
        assertThat(result.lastName()).isEqualTo("山田");
        verify(userRepository).findById(testUserId);
    }

    @Test
    @DisplayName("存在しないIDが指定された場合、ResourceNotFoundExceptionをスローする")
    void should_throwResourceNotFoundException_when_userNotFound() {
        // Arrange
        UUID unknownId = UUID.randomUUID();
        when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> userService.getCurrentUserProfile(unknownId))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("User が見つかりません: " + unknownId);
        verify(userRepository).findById(unknownId);
    }

    @Test
    @DisplayName("有効なリクエストでプロフィールを更新した場合、更新されたプロフィールを返す")
    void should_returnUpdatedProfile_when_validUpdateRequest() {
        // Arrange
        UpdateProfileRequest request = new UpdateProfileRequest("花子", "鈴木", "09012345678", LocalDate.of(1990, 1, 1));
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        doNothing().when(userEventProducer).publishUserUpdated(any(UserUpdatedEvent.class));

        // Act
        UserProfileResponse result = userService.updateProfile(testUserId, request);

        // Assert
        assertThat(result).isNotNull();
        verify(userRepository).findById(testUserId);
        verify(userRepository).save(testUser);
        verify(userEventProducer).publishUserUpdated(any(UserUpdatedEvent.class));
    }

    @Test
    @DisplayName("存在しないユーザーのプロフィールを更新しようとした場合、ResourceNotFoundExceptionをスローする")
    void should_throwResourceNotFoundException_when_updatingNonExistentUser() {
        // Arrange
        UUID unknownId = UUID.randomUUID();
        UpdateProfileRequest request = new UpdateProfileRequest("花子", "鈴木", null, null);
        when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> userService.updateProfile(unknownId, request))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("User が見つかりません: " + unknownId);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("ロールを持つユーザーのプロフィールを取得した場合、ロール名リストを含む")
    void should_includeRoleNames_when_userHasRoles() {
        // Arrange
        Role customerRole = new Role();
        customerRole.setId(1L);
        customerRole.setName(RoleName.CUSTOMER);
        testUser.setRoles(Set.of(customerRole));
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));

        // Act
        UserProfileResponse result = userService.getCurrentUserProfile(testUserId);

        // Assert
        assertThat(result.roles()).containsExactly("CUSTOMER");
    }
}
