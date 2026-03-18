package com.example.skishop.usermanagement.controller;

import com.example.skishop.usermanagement.dto.request.AdminUpdateUserRequest;
import com.example.skishop.usermanagement.dto.request.AssignRoleRequest;
import com.example.skishop.usermanagement.dto.response.AdminUserResponse;
import com.example.skishop.usermanagement.dto.response.UserActivityResponse;
import com.example.skishop.usermanagement.service.AdminUserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('STORE_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<Page<AdminUserResponse>> listUsers(
        @PageableDefault(size = 20) Pageable pageable,
        @RequestParam(required = false) String nameFilter
    ) {
        return ResponseEntity.ok(adminUserService.listUsers(pageable, nameFilter));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('STORE_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<AdminUserResponse> getUserById(@PathVariable UUID id) {
        return ResponseEntity.ok(adminUserService.getUserById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('STORE_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<AdminUserResponse> updateUser(
        @PathVariable UUID id,
        @Valid @RequestBody AdminUpdateUserRequest request
    ) {
        return ResponseEntity.ok(adminUserService.updateUser(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        adminUserService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/roles")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<AdminUserResponse> assignRole(
        @PathVariable UUID id,
        @Valid @RequestBody AssignRoleRequest request
    ) {
        return ResponseEntity.ok(adminUserService.assignRole(id, request.roleId()));
    }

    @DeleteMapping("/{id}/roles/{roleId}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<AdminUserResponse> revokeRole(
        @PathVariable UUID id,
        @PathVariable Long roleId
    ) {
        return ResponseEntity.ok(adminUserService.revokeRole(id, roleId));
    }

    @GetMapping("/{id}/activities")
    @PreAuthorize("hasAnyRole('STORE_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<Page<UserActivityResponse>> getUserActivities(
        @PathVariable UUID id,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(adminUserService.getUserActivities(id, pageable));
    }
}
