package com.example.skishop.usermanagement.controller;

import com.example.skishop.usermanagement.dto.request.AddressRequest;
import com.example.skishop.usermanagement.dto.request.UpdatePreferencesRequest;
import com.example.skishop.usermanagement.dto.request.UpdateProfileRequest;
import com.example.skishop.usermanagement.dto.response.AddressResponse;
import com.example.skishop.usermanagement.dto.response.UserPreferenceResponse;
import com.example.skishop.usermanagement.dto.response.UserProfileResponse;
import com.example.skishop.usermanagement.service.AddressService;
import com.example.skishop.usermanagement.service.UserPreferenceService;
import com.example.skishop.usermanagement.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;
    private final AddressService addressService;
    private final UserPreferenceService userPreferenceService;

    public UserController(
        UserService userService,
        AddressService addressService,
        UserPreferenceService userPreferenceService
    ) {
        this.userService = userService;
        this.addressService = addressService;
        this.userPreferenceService = userPreferenceService;
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserProfileResponse> getMyProfile(Authentication auth) {
        UUID userId = getCurrentUserId(auth);
        return ResponseEntity.ok(userService.getCurrentUserProfile(userId));
    }

    @PutMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserProfileResponse> updateMyProfile(
        @Valid @RequestBody UpdateProfileRequest request,
        Authentication auth
    ) {
        UUID userId = getCurrentUserId(auth);
        return ResponseEntity.ok(userService.updateProfile(userId, request));
    }

    @GetMapping("/me/addresses")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<AddressResponse>> getMyAddresses(Authentication auth) {
        UUID userId = getCurrentUserId(auth);
        return ResponseEntity.ok(addressService.getAddresses(userId));
    }

    @PostMapping("/me/addresses")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AddressResponse> addMyAddress(
        @Valid @RequestBody AddressRequest request,
        Authentication auth
    ) {
        UUID userId = getCurrentUserId(auth);
        AddressResponse response = addressService.addAddress(userId, request);
        return ResponseEntity.created(URI.create("/api/v1/users/me/addresses/" + response.id()))
            .body(response);
    }

    @PutMapping("/me/addresses/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AddressResponse> updateMyAddress(
        @PathVariable Long id,
        @Valid @RequestBody AddressRequest request,
        Authentication auth
    ) {
        UUID userId = getCurrentUserId(auth);
        return ResponseEntity.ok(addressService.updateAddress(userId, id, request));
    }

    @DeleteMapping("/me/addresses/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteMyAddress(
        @PathVariable Long id,
        Authentication auth
    ) {
        UUID userId = getCurrentUserId(auth);
        addressService.deleteAddress(userId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/preferences")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserPreferenceResponse> getMyPreferences(Authentication auth) {
        UUID userId = getCurrentUserId(auth);
        return ResponseEntity.ok(userPreferenceService.getPreferences(userId));
    }

    @PutMapping("/me/preferences")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserPreferenceResponse> updateMyPreferences(
        @Valid @RequestBody UpdatePreferencesRequest request,
        Authentication auth
    ) {
        UUID userId = getCurrentUserId(auth);
        return ResponseEntity.ok(userPreferenceService.updatePreferences(userId, request));
    }

    private UUID getCurrentUserId(Authentication auth) {
        return UUID.fromString(auth.getName());
    }
}
