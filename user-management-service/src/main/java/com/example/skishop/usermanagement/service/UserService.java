package com.example.skishop.usermanagement.service;

import com.example.skishop.usermanagement.dto.request.UpdateProfileRequest;
import com.example.skishop.usermanagement.dto.response.UserProfileResponse;
import com.example.skishop.usermanagement.event.UserEventProducer;
import com.example.skishop.usermanagement.event.UserUpdatedEvent;
import com.example.skishop.usermanagement.exception.ResourceNotFoundException;
import com.example.skishop.usermanagement.model.User;
import com.example.skishop.usermanagement.model.UserActivity;
import com.example.skishop.usermanagement.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final UserEventProducer userEventProducer;

    public UserService(UserRepository userRepository, UserEventProducer userEventProducer) {
        this.userRepository = userRepository;
        this.userEventProducer = userEventProducer;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUserProfile(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        return toUserProfileResponse(user);
    }

    public UserProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setPhoneNumber(request.phoneNumber());
        user.setBirthDate(request.birthDate());

        User saved = userRepository.save(user);
        log.info("ユーザープロフィールを更新しました: userId={}", userId);

        userEventProducer.publishUserUpdated(new UserUpdatedEvent(
            saved.getId(),
            saved.getEmail(),
            saved.getFirstName(),
            saved.getLastName(),
            saved.getUpdatedAt()
        ));

        return toUserProfileResponse(saved);
    }

    public void logActivity(UUID userId, String activityType, String details, String ipAddress, String deviceInfo) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        UserActivity activity = new UserActivity();
        activity.setUserId(userId);
        activity.setActivityType(activityType);
        activity.setTimestamp(OffsetDateTime.now());
        activity.setDetails(details);
        activity.setIpAddress(ipAddress);
        activity.setDeviceInfo(deviceInfo);

        user.getActivities().add(activity);
        userRepository.save(user);
        log.info("ユーザーアクティビティを記録しました: userId={}, type={}", userId, activityType);
    }

    private UserProfileResponse toUserProfileResponse(User user) {
        List<String> roleNames = user.getRoles().stream()
            .map(role -> role.getName().name())
            .toList();
        return new UserProfileResponse(
            user.getId(),
            user.getEmail(),
            user.getFirstName(),
            user.getLastName(),
            user.getPhoneNumber(),
            user.getBirthDate(),
            user.getCreatedAt(),
            user.getStatus(),
            roleNames
        );
    }
}
