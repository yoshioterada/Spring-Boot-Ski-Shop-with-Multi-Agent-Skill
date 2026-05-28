package com.example.skishop.ai.service;

import com.example.skishop.ai.dto.*;
import com.example.skishop.ai.model.ModelTraining;
import com.example.skishop.ai.model.ModelVersion;
import com.example.skishop.ai.repository.ModelTrainingRepository;
import com.example.skishop.ai.repository.ModelVersionRepository;
import com.example.skishop.common.exception.BusinessRuleViolationException;
import com.example.skishop.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ModelManagementServiceTest {

    @Mock
    private ModelTrainingRepository modelTrainingRepository;
    @Mock
    private ModelVersionRepository modelVersionRepository;

    private ModelManagementService modelManagementService;

    @BeforeEach
    void setUp() {
        modelManagementService = new ModelManagementService(modelTrainingRepository, modelVersionRepository);
    }

    @Test
    @DisplayName("モデルトレーニングが正常に開始される")
    void should_startTraining_when_validRequest() {
        // Arrange
        ModelTrainingRequest request = new ModelTrainingRequest(
            "RECOMMENDATION", "collaborative_filtering",
            Map.of("epochs", 100, "trainingDataSize", 1200),
            List.of("price", "category"));
        when(modelTrainingRepository.save(any(ModelTraining.class))).thenAnswer(inv -> inv.getArgument(0));
        when(modelVersionRepository.save(any(ModelVersion.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        TrainingJobResponse response = modelManagementService.trainModel(request);

        // Assert
        assertThat(response.modelType()).isEqualTo("RECOMMENDATION");
        assertThat(response.algorithm()).isEqualTo("collaborative_filtering");
        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.endTime()).isNotNull();
        assertThat(response.metrics())
            .containsEntry("trainingDataSize", 1200L)
            .containsKey("artifactPath")
            .containsKey("validationScore");
        verify(modelTrainingRepository, times(2)).save(any(ModelTraining.class));
        verify(modelVersionRepository).save(any(ModelVersion.class));
    }

    @Test
    @DisplayName("トレーニングステータスが正常に取得される")
    void should_returnStatus_when_validTrainingId() {
        // Arrange
        ModelTraining training = new ModelTraining("RECOMMENDATION", "collaborative_filtering", "v1.0");
        training.setStatus("COMPLETED");
        when(modelTrainingRepository.findById(training.getId())).thenReturn(Optional.of(training));

        // Act
        TrainingJobResponse response = modelManagementService.getTrainingStatus(training.getId());

        // Assert
        assertThat(response.status()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("存在しないトレーニングIDで例外がスローされる")
    void should_throwException_when_trainingNotFound() {
        // Arrange
        when(modelTrainingRepository.findById("nonexistent")).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> modelManagementService.getTrainingStatus("nonexistent"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("モデルバージョン一覧が取得される")
    void should_returnVersions_when_validModelType() {
        // Arrange
        ModelTraining training = new ModelTraining("RECOMMENDATION", "algo", "v1");
        ModelVersion version = new ModelVersion(training.getId(), "v1.0", "/models/rec-v1");
        when(modelTrainingRepository.findByModelType("RECOMMENDATION")).thenReturn(List.of(training));
        when(modelVersionRepository.findByModelTrainingId(training.getId())).thenReturn(List.of(version));

        // Act
        List<ModelVersionResponse> result = modelManagementService.getModelVersions("RECOMMENDATION");

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().version()).isEqualTo("v1.0");
    }

    @Test
    @DisplayName("モデルデプロイが正常に処理される")
    void should_deployModel_when_validRequest() {
        // Arrange
        ModelTraining training = new ModelTraining("RECOMMENDATION", "algo", "v1.0");
        training.setStatus("COMPLETED");
        ModelVersion previousVersion = new ModelVersion(training.getId(), "v0.9", "/models/rec-v0");
        previousVersion.setActive(true);
        ModelVersion version = new ModelVersion(training.getId(), "v1.0", "/models/rec-v1");
        when(modelVersionRepository.findById(version.getId())).thenReturn(Optional.of(version));
        when(modelTrainingRepository.findById(training.getId())).thenReturn(Optional.of(training));
        when(modelTrainingRepository.findByModelType("RECOMMENDATION")).thenReturn(List.of(training));
        when(modelVersionRepository.findByModelTrainingId(training.getId()))
                .thenReturn(List.of(previousVersion, version));
        when(modelVersionRepository.save(any(ModelVersion.class))).thenAnswer(inv -> inv.getArgument(0));
        ModelDeploymentRequest request = new ModelDeploymentRequest(version.getId(), true);

        // Act
        ModelVersionResponse response = modelManagementService.deployModel(request);

        // Assert
        assertThat(response.isActive()).isTrue();
        assertThat(response.deployedAt()).isNotNull();
        assertThat(previousVersion.isActive()).isFalse();
    }

    @Test
    @DisplayName("未完了トレーニングのモデルバージョンはデプロイできない")
    void should_throwException_when_trainingIsNotCompleted() {
        // Arrange
        ModelTraining training = new ModelTraining("RECOMMENDATION", "algo", "v1.0");
        training.setStatus("RUNNING");
        ModelVersion version = new ModelVersion(training.getId(), "v1.0", "/models/rec-v1");
        when(modelVersionRepository.findById(version.getId())).thenReturn(Optional.of(version));
        when(modelTrainingRepository.findById(training.getId())).thenReturn(Optional.of(training));
        ModelDeploymentRequest request = new ModelDeploymentRequest(version.getId(), true);

        // Act & Assert
        assertThatThrownBy(() -> modelManagementService.deployModel(request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Completed training is required");
    }

    @Test
    @DisplayName("存在しないモデルバージョンのデプロイで例外がスローされる")
    void should_throwException_when_modelVersionNotFound() {
        // Arrange
        when(modelVersionRepository.findById("nonexistent")).thenReturn(Optional.empty());
        ModelDeploymentRequest request = new ModelDeploymentRequest("nonexistent", true);

        // Act & Assert
        assertThatThrownBy(() -> modelManagementService.deployModel(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("モデルパフォーマンスが取得される")
    void should_returnPerformance_when_requested() {
        // Arrange
        ModelTraining training = new ModelTraining("RECOMMENDATION", "algo", "v1.0");
        training.setStatus("COMPLETED");
        training.setTrainingDataSize(500L);
        training.setValidationScore(0.81);
        ModelVersion version = new ModelVersion(training.getId(), "v1.0", "/models/rec-v1");
        version.setActive(true);
        version.setPerformance(Map.of("precision", 0.77, "recall", 0.72));
        when(modelTrainingRepository.findByModelType("RECOMMENDATION")).thenReturn(List.of(training));
        when(modelVersionRepository.findByModelTrainingId(training.getId())).thenReturn(List.of(version));
        when(modelTrainingRepository.findById(training.getId())).thenReturn(Optional.of(training));

        // Act
        ModelPerformanceResponse response = modelManagementService.getModelPerformance("RECOMMENDATION", "v1.0");

        // Assert
        assertThat(response.modelType()).isEqualTo("RECOMMENDATION");
        assertThat(response.version()).isEqualTo("v1.0");
        assertThat(response.metrics())
                .containsEntry("precision", 0.77)
                .containsEntry("active", true)
                .containsEntry("modelPath", "/models/rec-v1")
                .containsEntry("trainingDataSize", 500L);
    }

    @Test
    @DisplayName("activeモデルが存在する場合、descriptorがバージョン付きで返る")
    void should_returnActiveModelDescriptor_when_activeVersionExists() {
        // Arrange
        ModelTraining training = new ModelTraining("SEARCH", "semantic_search", "v1.0");
        ModelVersion version = new ModelVersion(training.getId(), "v1.0", "/models/search-v1");
        version.setActive(true);
        when(modelTrainingRepository.findByModelType("SEARCH")).thenReturn(List.of(training));
        when(modelVersionRepository.findByModelTrainingId(training.getId())).thenReturn(List.of(version));

        // Act
        String descriptor = modelManagementService.resolveActiveModelDescriptor("SEARCH");

        // Assert
        assertThat(descriptor).isEqualTo("SEARCH:v1.0");
    }
}
