package com.example.skishop.usermanagement.service;

import com.example.skishop.common.event.EventPublisher;
import com.example.skishop.common.exception.BusinessRuleViolationException;
import com.example.skishop.common.exception.ResourceNotFoundException;
import com.example.skishop.usermanagement.dto.*;
import com.example.skishop.usermanagement.model.RoleEntity;
import com.example.skishop.usermanagement.model.UserActivity;
import com.example.skishop.usermanagement.model.UserPreference;
import com.example.skishop.usermanagement.model.UserProfile;
import com.example.skishop.usermanagement.repository.RoleEntityRepository;
import com.example.skishop.usermanagement.repository.UserActivityRepository;
import com.example.skishop.usermanagement.repository.UserPreferenceRepository;
import com.example.skishop.usermanagement.model.VerificationToken;
import com.example.skishop.usermanagement.repository.UserProfileRepository;
import com.example.skishop.usermanagement.repository.VerificationTokenRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserProfileRepository userProfileRepository;
    @Mock private RoleEntityRepository roleEntityRepository;
    @Mock private UserPreferenceRepository userPreferenceRepository;
    @Mock private UserActivityRepository userActivityRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EventPublisher eventPublisher;
    @Mock private VerificationTokenRepository verificationTokenRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userProfileRepository, roleEntityRepository,
                userPreferenceRepository, userActivityRepository, passwordEncoder, eventPublisher,
                verificationTokenRepository);
    }

    private UserProfile createTestUser() {
        var user = new UserProfile("test@example.com", "encoded", "太郎", "山田");
        user.setStatus(UserProfile.UserStatus.ACTIVE);
        return user;
    }

    @Nested
    @DisplayName("ユーザー作成")
    class CreateUser {

        @Test
        @DisplayName("有効な情報でユーザー作成が成功する")
        void should_createUser_when_validRequest() {
            var request = new CreateUserRequest("test@example.com", "Password1!", "太郎", "山田", "090-1234-5678", null, "MALE");
            var role = new RoleEntity("CUSTOMER", "Default customer role");
            when(userProfileRepository.existsByEmail("test@example.com")).thenReturn(false);
            when(passwordEncoder.encode("Password1!")).thenReturn("encoded");
            when(roleEntityRepository.findByName("CUSTOMER")).thenReturn(Optional.of(role));
            when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

            UserResponse response = userService.createUser(request);

            assertThat(response.email()).isEqualTo("test@example.com");
            assertThat(response.firstName()).isEqualTo("太郎");
            verify(eventPublisher).publish(any());
        }

        @Test
        @DisplayName("既存メールアドレスで作成時にBusinessRuleViolationExceptionがスローされる")
        void should_throwException_when_emailExists() {
            var request = new CreateUserRequest("existing@example.com", "Password1!", "太郎", "山田", null, null, null);
            when(userProfileRepository.existsByEmail("existing@example.com")).thenReturn(true);

            assertThatThrownBy(() -> userService.createUser(request))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("既に登録されています");
        }
    }

    @Nested
    @DisplayName("ユーザー取得")
    class GetUser {

        @Test
        @DisplayName("存在するユーザーIDで情報取得が成功する")
        void should_getUser_when_validId() {
            UUID userId = UUID.randomUUID();
            when(userProfileRepository.findById(userId)).thenReturn(Optional.of(createTestUser()));

            UserResponse response = userService.getUserById(userId);

            assertThat(response.email()).isEqualTo("test@example.com");
        }

        @Test
        @DisplayName("存在しないユーザーIDでResourceNotFoundExceptionがスローされる")
        void should_throwNotFound_when_userNotExists() {
            UUID userId = UUID.randomUUID();
            when(userProfileRepository.findById(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getUserById(userId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("ユーザー更新")
    class UpdateUser {

        @Test
        @DisplayName("有効なリクエストでユーザー情報更新が成功する")
        void should_updateUser_when_validRequest() {
            UUID userId = UUID.randomUUID();
            var user = createTestUser();
            var request = new UpdateUserRequest("次郎", "田中", "080-9999-8888", null, null);
            when(userProfileRepository.findById(userId)).thenReturn(Optional.of(user));
            when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

            UserResponse response = userService.updateUser(userId, request);

            assertThat(response.firstName()).isEqualTo("次郎");
            assertThat(response.lastName()).isEqualTo("田中");
            verify(eventPublisher).publish(any());
        }
    }

    @Nested
    @DisplayName("パスワード変更")
    class ChangePassword {

        @Test
        @DisplayName("正しい現在のパスワードでパスワード変更が成功する")
        void should_changePassword_when_currentPasswordCorrect() {
            UUID userId = UUID.randomUUID();
            var user = createTestUser();
            when(userProfileRepository.findById(userId)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("OldPass1!", "encoded")).thenReturn(true);
            when(passwordEncoder.encode("NewPass1!")).thenReturn("newEncoded");

            userService.changePassword(userId, new ChangePasswordRequest("OldPass1!", "NewPass1!"));

            verify(userProfileRepository).save(any(UserProfile.class));
            verify(eventPublisher).publish(any());
        }

        @Test
        @DisplayName("間違った現在のパスワードでパスワード変更が失敗する")
        void should_throwException_when_wrongCurrentPassword() {
            UUID userId = UUID.randomUUID();
            var user = createTestUser();
            when(userProfileRepository.findById(userId)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("WrongPass1!", "encoded")).thenReturn(false);

            assertThatThrownBy(() -> userService.changePassword(userId, new ChangePasswordRequest("WrongPass1!", "NewPass1!")))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("パスワードが正しくありません");
        }
    }

    @Nested
    @DisplayName("ユーザー削除")
    class DeleteUser {

        @Test
        @DisplayName("アクティブユーザーの削除が成功する")
        void should_softDelete_when_activeUser() {
            UUID userId = UUID.randomUUID();
            var user = createTestUser();
            when(userProfileRepository.findById(userId)).thenReturn(Optional.of(user));
            when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

            UserResponse response = userService.deleteUser(userId);

            assertThat(response.status()).isEqualTo("DEACTIVATED");
            verify(eventPublisher).publish(any());
        }

        @Test
        @DisplayName("既に無効化されたユーザーの削除で例外がスローされる")
        void should_throwException_when_alreadyDeactivated() {
            UUID userId = UUID.randomUUID();
            var user = createTestUser();
            user.setStatus(UserProfile.UserStatus.DEACTIVATED);
            when(userProfileRepository.findById(userId)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> userService.deleteUser(userId))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("既に無効化されています");
        }
    }

    @Nested
    @DisplayName("ステータス管理")
    class StatusManagement {

        @Test
        @DisplayName("ユーザーステータス更新が成功する")
        void should_updateStatus_when_validRequest() {
            UUID userId = UUID.randomUUID();
            var user = createTestUser();
            when(userProfileRepository.findById(userId)).thenReturn(Optional.of(user));

            userService.updateUserStatus(userId, "SUSPENDED");

            assertThat(user.getStatus()).isEqualTo(UserProfile.UserStatus.SUSPENDED);
            verify(userProfileRepository).save(any(UserProfile.class));
            verify(eventPublisher).publish(any());
        }

        @Test
        @DisplayName("ユーザー一覧取得が成功する")
        void should_returnPagedUsers_when_listUsers() {
            var user = createTestUser();
            Pageable pageable = PageRequest.of(0, 20);
            when(userProfileRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(user), pageable, 1));

            Page<UserResponse> result = userService.listUsers(pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().getFirst().email()).isEqualTo("test@example.com");
        }
    }

    @Nested
    @DisplayName("メール検証")
    class EmailVerification {

        @Test
        @DisplayName("メールアドレスの存在チェックが正しく動作する")
        void should_returnTrue_when_emailExists() {
            when(userProfileRepository.existsByEmail("test@example.com")).thenReturn(true);

            assertThat(userService.checkEmailExists("test@example.com")).isTrue();
        }

        @Test
        @DisplayName("メールアドレスが存在しない場合falseを返す")
        void should_returnFalse_when_emailNotExists() {
            when(userProfileRepository.existsByEmail("new@example.com")).thenReturn(false);

            assertThat(userService.checkEmailExists("new@example.com")).isFalse();
        }

        @Test
        @DisplayName("メール検証が成功する")
        void should_verifyEmail_when_validToken() {
            // Arrange
            UUID userId = UUID.randomUUID();
            var user = new UserProfile("test@example.com", "encoded", "太郎", "山田");
            user.setStatus(UserProfile.UserStatus.PENDING_VERIFICATION);
            when(userProfileRepository.findById(userId)).thenReturn(Optional.of(user));

            var token = new VerificationToken(userId, "token-value", Instant.now().plusSeconds(3600));
            when(verificationTokenRepository.findByTokenAndUsedFalse("token-value")).thenReturn(Optional.of(token));

            // Act
            userService.verifyEmail("token-value");

            // Assert
            assertThat(user.isEmailVerified()).isTrue();
            assertThat(user.getStatus()).isEqualTo(UserProfile.UserStatus.ACTIVE);
            verify(verificationTokenRepository).save(any(VerificationToken.class));
            verify(eventPublisher).publish(any());
        }
    }

    @Nested
    @DisplayName("ロール管理")
    class RoleManagement {

        @Test
        @DisplayName("ロール割り当てが成功する")
        void should_assignRole_when_validRequest() {
            UUID userId = UUID.randomUUID();
            var user = createTestUser();
            var role = new RoleEntity("ADMIN", "Administrator");
            when(userProfileRepository.findById(userId)).thenReturn(Optional.of(user));
            when(roleEntityRepository.findByName("ADMIN")).thenReturn(Optional.of(role));
            when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

            UserResponse response = userService.assignRole(userId, "ADMIN");

            assertThat(response.roleName()).isEqualTo("ADMIN");
            verify(eventPublisher).publish(any());
        }

        @Test
        @DisplayName("存在しないロールの割り当てで例外がスローされる")
        void should_throwNotFound_when_roleNotExists() {
            UUID userId = UUID.randomUUID();
            var user = createTestUser();
            when(userProfileRepository.findById(userId)).thenReturn(Optional.of(user));
            when(roleEntityRepository.findByName("NONEXISTENT")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.assignRole(userId, "NONEXISTENT"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("プリファレンス管理")
    class PreferenceManagement {

        @Test
        @DisplayName("プリファレンス一覧取得が成功する")
        void should_returnPreferences_when_validUserId() {
            UUID userId = UUID.randomUUID();
            var user = createTestUser();
            var pref = new UserPreference(userId, "language", "ja", "STRING");
            when(userProfileRepository.findById(userId)).thenReturn(Optional.of(user));
            when(userPreferenceRepository.findByUserId(userId)).thenReturn(List.of(pref));

            List<PreferenceResponse> result = userService.getPreferences(userId);

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().key()).isEqualTo("language");
        }

        @Test
        @DisplayName("単一プリファレンス取得が成功する")
        void should_returnPreference_when_keyExists() {
            UUID userId = UUID.randomUUID();
            var user = createTestUser();
            var pref = new UserPreference(userId, "language", "ja", "STRING");
            when(userProfileRepository.findById(userId)).thenReturn(Optional.of(user));
            when(userPreferenceRepository.findByUserIdAndPrefKey(userId, "language")).thenReturn(Optional.of(pref));

            PreferenceResponse result = userService.getPreference(userId, "language");

            assertThat(result.key()).isEqualTo("language");
            assertThat(result.value()).isEqualTo("ja");
        }

        @Test
        @DisplayName("存在しないプリファレンスキーで例外がスローされる")
        void should_throwNotFound_when_preferenceKeyNotExists() {
            UUID userId = UUID.randomUUID();
            var user = createTestUser();
            when(userProfileRepository.findById(userId)).thenReturn(Optional.of(user));
            when(userPreferenceRepository.findByUserIdAndPrefKey(userId, "unknown")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getPreference(userId, "unknown"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("アクティビティ管理")
    class ActivityManagement {

        @Test
        @DisplayName("アクティビティ一覧取得が成功する")
        void should_returnActivities_when_validUserId() {
            UUID userId = UUID.randomUUID();
            var user = createTestUser();
            var activity = new UserActivity(userId, "LOGIN", "ログイン", "192.168.1.1", "Chrome");
            Pageable pageable = PageRequest.of(0, 20);
            when(userProfileRepository.findById(userId)).thenReturn(Optional.of(user));
            when(userActivityRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable))
                    .thenReturn(new PageImpl<>(List.of(activity), pageable, 1));

            Page<ActivityResponse> result = userService.getActivities(userId, pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().getFirst().activityType()).isEqualTo("LOGIN");
        }

        @Test
        @DisplayName("アクティビティ記録が成功する")
        void should_recordActivity_when_validData() {
            UUID userId = UUID.randomUUID();

            userService.recordActivity(userId, "LOGIN", "ログイン", "192.168.1.1", "Chrome");

            verify(userActivityRepository).save(any(UserActivity.class));
        }
    }
}
