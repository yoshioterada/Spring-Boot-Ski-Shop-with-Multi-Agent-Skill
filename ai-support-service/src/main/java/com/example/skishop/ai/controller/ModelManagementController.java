package com.example.skishop.ai.controller;

import com.example.skishop.ai.dto.*;
import com.example.skishop.ai.service.ModelManagementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/models")
@PreAuthorize("hasRole('ADMIN')")
public class ModelManagementController {

    private final ModelManagementService modelManagementService;

    public ModelManagementController(ModelManagementService modelManagementService) {
        this.modelManagementService = modelManagementService;
    }

    @PostMapping("/train")
    public ResponseEntity<TrainingJobResponse> trainModel(
            @Valid @RequestBody ModelTrainingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(modelManagementService.trainModel(request));
    }

    @GetMapping("/status/{trainingId}")
    public ResponseEntity<TrainingJobResponse> getTrainingStatus(
            @PathVariable String trainingId) {
        return ResponseEntity.ok(modelManagementService.getTrainingStatus(trainingId));
    }

    @GetMapping("/versions")
    public ResponseEntity<List<ModelVersionResponse>> getModelVersions(
            @RequestParam String modelType) {
        return ResponseEntity.ok(modelManagementService.getModelVersions(modelType));
    }

    @PostMapping("/deploy")
    public ResponseEntity<ModelVersionResponse> deployModel(
            @Valid @RequestBody ModelDeploymentRequest request) {
        return ResponseEntity.ok(modelManagementService.deployModel(request));
    }

    @GetMapping("/performance")
    public ResponseEntity<ModelPerformanceResponse> getModelPerformance(
            @RequestParam String modelType,
            @RequestParam(required = false) String version) {
        return ResponseEntity.ok(modelManagementService.getModelPerformance(modelType, version));
    }
}
