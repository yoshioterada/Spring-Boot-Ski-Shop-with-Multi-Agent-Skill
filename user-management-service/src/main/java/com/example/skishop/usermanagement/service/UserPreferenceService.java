package com.example.skishop.usermanagement.service;

import com.example.skishop.usermanagement.dto.request.UpdatePreferencesRequest;
import com.example.skishop.usermanagement.dto.response.UserPreferenceResponse;
import com.example.skishop.usermanagement.model.UserPreference;
import com.example.skishop.usermanagement.repository.UserPreferenceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@Transactional
public class UserPreferenceService {

    private final UserPreferenceRepository userPreferenceRepository;

    public UserPreferenceService(UserPreferenceRepository userPreferenceRepository) {
        this.userPreferenceRepository = userPreferenceRepository;
    }

    @Transactional(readOnly = true)
    public UserPreferenceResponse getPreferences(UUID userId) {
        UserPreference pref = userPreferenceRepository.findByUserId(userId)
            .orElseGet(() -> {
                UserPreference newPref = new UserPreference();
                newPref.setUserId(userId);
                newPref.setLanguage("ja");
                newPref.setCurrency("JPY");
                return newPref;
            });
        return toResponse(pref);
    }

    public UserPreferenceResponse updatePreferences(UUID userId, UpdatePreferencesRequest request) {
        UserPreference pref = userPreferenceRepository.findByUserId(userId)
            .orElseGet(() -> {
                UserPreference newPref = new UserPreference();
                newPref.setUserId(userId);
                return newPref;
            });
        if (request.language() != null) {
            pref.setLanguage(request.language());
        }
        if (request.currency() != null) {
            pref.setCurrency(request.currency());
        }
        UserPreference saved = userPreferenceRepository.save(pref);
        log.info("ユーザー設定を更新しました: userId={}", userId);
        return toResponse(saved);
    }

    private UserPreferenceResponse toResponse(UserPreference pref) {
        return new UserPreferenceResponse(
            pref.getId(),
            pref.getUserId(),
            pref.getLanguage(),
            pref.getCurrency()
        );
    }
}
