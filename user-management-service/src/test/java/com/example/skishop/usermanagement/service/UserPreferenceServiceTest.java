package com.example.skishop.usermanagement.service;

import com.example.skishop.usermanagement.dto.request.UpdatePreferencesRequest;
import com.example.skishop.usermanagement.dto.response.UserPreferenceResponse;
import com.example.skishop.usermanagement.model.UserPreference;
import com.example.skishop.usermanagement.repository.UserPreferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserPreferenceServiceTest {

    @InjectMocks
    private UserPreferenceService userPreferenceService;

    @Mock
    private UserPreferenceRepository userPreferenceRepository;

    private UUID userId;
    private UserPreference testPreference;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        testPreference = new UserPreference();
        testPreference.setId(1L);
        testPreference.setUserId(userId);
        testPreference.setLanguage("ja");
        testPreference.setCurrency("JPY");
    }

    @Test
    @DisplayName("既存のユーザー設定が存在する場合、その設定を返す")
    void should_returnExistingPreferences_when_preferencesExist() {
        // Arrange
        when(userPreferenceRepository.findByUserId(userId)).thenReturn(Optional.of(testPreference));

        // Act
        UserPreferenceResponse result = userPreferenceService.getPreferences(userId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.language()).isEqualTo("ja");
        assertThat(result.currency()).isEqualTo("JPY");
        verify(userPreferenceRepository).findByUserId(userId);
    }

    @Test
    @DisplayName("ユーザー設定が存在しない場合、デフォルト値で返す")
    void should_returnDefaultPreferences_when_preferencesNotExist() {
        // Arrange
        when(userPreferenceRepository.findByUserId(userId)).thenReturn(Optional.empty());

        // Act
        UserPreferenceResponse result = userPreferenceService.getPreferences(userId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.language()).isEqualTo("ja");
        assertThat(result.currency()).isEqualTo("JPY");
    }

    @Test
    @DisplayName("有効なリクエストでユーザー設定を更新した場合、更新された設定を返す")
    void should_returnUpdatedPreferences_when_validUpdateRequest() {
        // Arrange
        UpdatePreferencesRequest request = new UpdatePreferencesRequest("en", "USD");
        when(userPreferenceRepository.findByUserId(userId)).thenReturn(Optional.of(testPreference));
        when(userPreferenceRepository.save(any(UserPreference.class))).thenReturn(testPreference);

        // Act
        UserPreferenceResponse result = userPreferenceService.updatePreferences(userId, request);

        // Assert
        assertThat(result).isNotNull();
        verify(userPreferenceRepository).save(testPreference);
    }

    @Test
    @DisplayName("ユーザー設定が存在しない場合、新規作成して更新する")
    void should_createAndUpdatePreferences_when_preferencesNotExist() {
        // Arrange
        UpdatePreferencesRequest request = new UpdatePreferencesRequest("en", "USD");
        UserPreference newPref = new UserPreference();
        newPref.setUserId(userId);
        newPref.setLanguage("en");
        newPref.setCurrency("USD");
        when(userPreferenceRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(userPreferenceRepository.save(any(UserPreference.class))).thenReturn(newPref);

        // Act
        UserPreferenceResponse result = userPreferenceService.updatePreferences(userId, request);

        // Assert
        assertThat(result).isNotNull();
        verify(userPreferenceRepository).save(any(UserPreference.class));
    }
}
