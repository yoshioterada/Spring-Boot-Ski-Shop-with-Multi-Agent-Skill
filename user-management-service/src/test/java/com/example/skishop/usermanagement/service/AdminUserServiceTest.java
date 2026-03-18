package com.example.skishop.usermanagement.service;

import com.example.skishop.usermanagement.dto.request.AdminUpdateUserRequest;
import com.example.skishop.usermanagement.dto.response.AdminUserResponse;
import com.example.skishop.usermanagement.dto.response.UserActivityResponse;
import com.example.skishop.usermanagement.event.UserDeletedEvent;
import com.example.skishop.usermanagement.event.UserEventProducer;
import com.example.skishop.usermanagement.event.UserUpdatedEvent;
import com.example.skishop.usermanagement.exception.ResourceNotFoundException;
import com.example.skishop.usermanagement.model.Role;
import com.example.skishop.usermanagement.model.RoleName;
import com.example.skishop.usermanagement.model.User;
import com.example.skishop.usermanagement.model.UserActivity;
import com.example.skishop.usermanagement.model.UserStatus;
import com.example.skishop.usermanagement.repository.RoleRepository;
import com.example.skishop.usermanagement.repository.UserActivityRepository;
import com.example.skishop.usermanagement.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @InjectMocks
    private AdminUserService adminUserService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserActivityRepository userActivityRepository;

    @Mock
    private UserEventProducer userEventProducer;

    private User testUser;
    private UUID testUserId;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testUser = new User();
        testUser.setId(testUserId);
        testUser.setEmail("admin@example.com");
        testUser.setPasswordHash("hashedPassword");
        testUser.setFirstName("管理者");
        testUser.setLastName("テスト");
        testUser.setStatus(UserStatus.ACTIVE);
        testUser.setCreatedAt(OffsetDateTime.now());
        testUser.setUpdatedAt(OffsetDateTime.now());
        pageable = PageRequest.of(0, 20);
    }

    @Test
    @DisplayName("フィルターなしでユーザー一覧を取得した場合、全ユーザーを返す")
    void should_returnAllUsers_when_noFilterProvided() {
        // Arrange
        Page<User> userPage = new PageImpl<>(List.of(testUser));
        when(userRepository.findAll(pageable)).thenReturn(userPage);

        // Act
        Page<AdminUserResponse> result = adminUserService.listUsers(pageable, null);

        // Assert
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).email()).isEqualTo("admin@example.com");
        verify(userRepository).findAll(pageable);
    }

    @Test
    @DisplayName("有効なIDでユーザーを取得した場合、ユーザー情報を返す")
    void should_returnUser_when_validIdProvided() {
        // Arrange
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));

        // Act
        AdminUserResponse result = adminUserService.getUserById(testUserId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(testUserId);
        assertThat(result.email()).isEqualTo("admin@example.com");
    }

    @Test
    @DisplayName("存在しないIDでユーザーを取得しようとした場合、ResourceNotFoundExceptionをスローする")
    void should_throwResourceNotFoundException_when_userNotFoundById() {
        // Arrange
        UUID unknownId = UUID.randomUUID();
        when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> adminUserService.getUserById(unknownId))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("User が見つかりません: " + unknownId);
    }

    @Test
    @DisplayName("有効なリクエストでユーザーを更新した場合、更新されたユーザーを返す")
    void should_returnUpdatedUser_when_validUpdateRequest() {
        // Arrange
        AdminUpdateUserRequest request = new AdminUpdateUserRequest("新太郎", "山田", null, UserStatus.ACTIVE);
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        doNothing().when(userEventProducer).publishUserUpdated(any(UserUpdatedEvent.class));

        // Act
        AdminUserResponse result = adminUserService.updateUser(testUserId, request);

        // Assert
        assertThat(result).isNotNull();
        verify(userRepository).save(testUser);
        verify(userEventProducer).publishUserUpdated(any(UserUpdatedEvent.class));
    }

    @Test
    @DisplayName("ユーザーを削除した場合、削除イベントが発行される")
    void should_publishDeleteEvent_when_userDeleted() {
        // Arrange
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        doNothing().when(userRepository).delete(testUser);
        doNothing().when(userEventProducer).publishUserDeleted(any(UserDeletedEvent.class));

        // Act
        adminUserService.deleteUser(testUserId);

        // Assert
        verify(userRepository).delete(testUser);
        verify(userEventProducer).publishUserDeleted(any(UserDeletedEvent.class));
    }

    @Test
    @DisplayName("存在しないユーザーを削除しようとした場合、ResourceNotFoundExceptionをスローする")
    void should_throwResourceNotFoundException_when_deletingNonExistentUser() {
        // Arrange
        UUID unknownId = UUID.randomUUID();
        when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> adminUserService.deleteUser(unknownId))
            .isInstanceOf(ResourceNotFoundException.class);
        verify(userRepository, never()).delete(any());
    }

    @Test
    @DisplayName("ロールを付与した場合、更新されたユーザーを返す")
    void should_returnUserWithRole_when_roleAssigned() {
        // Arrange
        Role role = new Role();
        role.setId(1L);
        role.setName(RoleName.CUSTOMER);
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // Act
        AdminUserResponse result = adminUserService.assignRole(testUserId, 1L);

        // Assert
        assertThat(result).isNotNull();
        verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("存在しないロールを付与しようとした場合、ResourceNotFoundExceptionをスローする")
    void should_throwResourceNotFoundException_when_roleNotFound() {
        // Arrange
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(roleRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> adminUserService.assignRole(testUserId, 999L))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Role が見つかりません: 999");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("ロールを剥奪した場合、更新されたユーザーを返す")
    void should_returnUserWithoutRole_when_roleRevoked() {
        // Arrange
        Role role = new Role();
        role.setId(1L);
        role.setName(RoleName.CUSTOMER);
        testUser.getRoles().add(role);
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // Act
        AdminUserResponse result = adminUserService.revokeRole(testUserId, 1L);

        // Assert
        assertThat(result).isNotNull();
        verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("ユーザーのアクティビティを取得した場合、アクティビティページを返す")
    void should_returnActivityPage_when_userExists() {
        // Arrange
        UserActivity activity = new UserActivity();
        activity.setId(1L);
        activity.setUserId(testUserId);
        activity.setActivityType("LOGIN");
        activity.setTimestamp(OffsetDateTime.now());

        Page<UserActivity> activityPage = new PageImpl<>(List.of(activity));
        when(userRepository.existsById(testUserId)).thenReturn(true);
        when(userActivityRepository.findByUserId(testUserId, pageable)).thenReturn(activityPage);

        // Act
        Page<UserActivityResponse> result = adminUserService.getUserActivities(testUserId, pageable);

        // Assert
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).activityType()).isEqualTo("LOGIN");
    }
}
