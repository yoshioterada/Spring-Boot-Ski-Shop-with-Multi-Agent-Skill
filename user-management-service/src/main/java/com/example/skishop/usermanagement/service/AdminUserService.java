package com.example.skishop.usermanagement.service;

import com.example.skishop.usermanagement.dto.request.AdminUpdateUserRequest;
import com.example.skishop.usermanagement.dto.response.AdminUserResponse;
import com.example.skishop.usermanagement.dto.response.UserActivityResponse;
import com.example.skishop.usermanagement.event.UserDeletedEvent;
import com.example.skishop.usermanagement.event.UserEventProducer;
import com.example.skishop.usermanagement.event.UserUpdatedEvent;
import com.example.skishop.usermanagement.exception.ResourceNotFoundException;
import com.example.skishop.usermanagement.model.Role;
import com.example.skishop.usermanagement.model.User;
import com.example.skishop.usermanagement.model.UserStatus;
import com.example.skishop.usermanagement.repository.RoleRepository;
import com.example.skishop.usermanagement.repository.UserActivityRepository;
import com.example.skishop.usermanagement.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Transactional
public class AdminUserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserActivityRepository userActivityRepository;
    private final UserEventProducer userEventProducer;

    public AdminUserService(
        UserRepository userRepository,
        RoleRepository roleRepository,
        UserActivityRepository userActivityRepository,
        UserEventProducer userEventProducer
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userActivityRepository = userActivityRepository;
        this.userEventProducer = userEventProducer;
    }

    @Transactional(readOnly = true)
    public Page<AdminUserResponse> listUsers(Pageable pageable, String nameFilter) {
        Page<User> users;
        if (nameFilter != null && !nameFilter.isBlank()) {
            users = userRepository.findByStatusAndFirstNameContainingIgnoreCase(
                UserStatus.ACTIVE, nameFilter, pageable);
        } else {
            users = userRepository.findAll(pageable);
        }
        return users.map(this::toAdminUserResponse);
    }

    @Transactional(readOnly = true)
    public AdminUserResponse getUserById(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        return toAdminUserResponse(user);
    }

    public AdminUserResponse updateUser(UUID userId, AdminUpdateUserRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (request.firstName() != null) user.setFirstName(request.firstName());
        if (request.lastName() != null) user.setLastName(request.lastName());
        if (request.phoneNumber() != null) user.setPhoneNumber(request.phoneNumber());
        if (request.status() != null) user.setStatus(request.status());

        User saved = userRepository.save(user);
        log.info("管理者がユーザーを更新しました: userId={}", userId);

        userEventProducer.publishUserUpdated(new UserUpdatedEvent(
            saved.getId(),
            saved.getEmail(),
            saved.getFirstName(),
            saved.getLastName(),
            saved.getUpdatedAt()
        ));

        return toAdminUserResponse(saved);
    }

    public void deleteUser(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        String email = user.getEmail();
        userRepository.delete(user);
        log.info("管理者がユーザーを削除しました: userId={}", userId);

        userEventProducer.publishUserDeleted(new UserDeletedEvent(
            userId,
            email,
            OffsetDateTime.now()
        ));
    }

    public AdminUserResponse assignRole(UUID userId, Long roleId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        Role role = roleRepository.findById(roleId)
            .orElseThrow(() -> new ResourceNotFoundException("Role", roleId));

        user.getRoles().add(role);
        User saved = userRepository.save(user);
        log.info("ロールを付与しました: userId={}, roleId={}", userId, roleId);
        return toAdminUserResponse(saved);
    }

    public AdminUserResponse revokeRole(UUID userId, Long roleId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        Role role = roleRepository.findById(roleId)
            .orElseThrow(() -> new ResourceNotFoundException("Role", roleId));

        user.getRoles().remove(role);
        User saved = userRepository.save(user);
        log.info("ロールを剥奪しました: userId={}, roleId={}", userId, roleId);
        return toAdminUserResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<UserActivityResponse> getUserActivities(UUID userId, Pageable pageable) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User", userId);
        }
        return userActivityRepository.findByUserId(userId, pageable)
            .map(activity -> new UserActivityResponse(
                activity.getId(),
                activity.getUserId(),
                activity.getActivityType(),
                activity.getTimestamp(),
                activity.getDetails(),
                activity.getIpAddress(),
                activity.getDeviceInfo()
            ));
    }

    private AdminUserResponse toAdminUserResponse(User user) {
        List<String> roleNames = user.getRoles().stream()
            .map(role -> role.getName().name())
            .toList();
        return new AdminUserResponse(
            user.getId(),
            user.getEmail(),
            user.getFirstName(),
            user.getLastName(),
            user.getPhoneNumber(),
            user.getBirthDate(),
            user.getCreatedAt(),
            user.getUpdatedAt(),
            user.getLastLoginAt(),
            user.getStatus(),
            roleNames
        );
    }
}
