package com.example.skishop.usermanagement.controller;

import com.example.skishop.usermanagement.dto.*;
import com.example.skishop.usermanagement.service.UserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        log.info("Create user request received");
        UserResponse response = userService.createUser(request);
        return ResponseEntity.created(URI.create("/api/v1/users/" + response.id())).body(response);
    }

    @GetMapping("/check-email")
    public ResponseEntity<EmailCheckResponse> checkEmail(@RequestParam String email) {
        return ResponseEntity.ok(new EmailCheckResponse(userService.checkEmailExists(email)));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        userService.verifyEmail(request.token());
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("#id == authentication.principal or hasRole('ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUser(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @PreAuthorize("#id == authentication.principal or hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(@PathVariable UUID id,
                                                    @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userService.updateUser(id, request));
    }

    @PreAuthorize("#id == authentication.principal or hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("#id == authentication.principal or hasRole('ADMIN')")
    @PutMapping("/{id}/password")
    public ResponseEntity<Void> changePassword(@PathVariable UUID id,
                                                @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(id, request);
        return ResponseEntity.noContent().build();
    }

    // --- Preferences ---

    @PreAuthorize("#id == authentication.principal or hasRole('ADMIN')")
    @GetMapping("/{id}/preferences")
    public ResponseEntity<List<PreferenceResponse>> getPreferences(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getPreferences(id));
    }

    @PreAuthorize("#id == authentication.principal or hasRole('ADMIN')")
    @GetMapping("/{id}/preferences/{key}")
    public ResponseEntity<PreferenceResponse> getPreference(@PathVariable UUID id, @PathVariable String key) {
        return ResponseEntity.ok(userService.getPreference(id, key));
    }

    @PreAuthorize("#id == authentication.principal or hasRole('ADMIN')")
    @PutMapping("/{id}/preferences/{key}")
    public ResponseEntity<PreferenceResponse> updatePreference(@PathVariable UUID id,
                                                                @PathVariable String key,
                                                                @Valid @RequestBody UpdatePreferenceRequest request) {
        return ResponseEntity.ok(userService.updatePreference(id, key, request));
    }

    @PreAuthorize("#id == authentication.principal or hasRole('ADMIN')")
    @DeleteMapping("/{id}/preferences/{key}")
    public ResponseEntity<Void> deletePreference(@PathVariable UUID id, @PathVariable String key) {
        userService.deletePreference(id, key);
        return ResponseEntity.noContent().build();
    }

    // --- Activities ---

    @PreAuthorize("#id == authentication.principal or hasRole('ADMIN')")
    @GetMapping("/{id}/activities")
    public ResponseEntity<Page<ActivityResponse>> getActivities(@PathVariable UUID id,
                                                                 @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(userService.getActivities(id, pageable));
    }
}
