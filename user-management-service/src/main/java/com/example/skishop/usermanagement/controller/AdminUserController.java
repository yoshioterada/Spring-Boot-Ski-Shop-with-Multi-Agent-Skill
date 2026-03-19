package com.example.skishop.usermanagement.controller;

import com.example.skishop.usermanagement.dto.AssignRoleRequest;
import com.example.skishop.usermanagement.dto.UserResponse;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private static final Logger log = LoggerFactory.getLogger(AdminUserController.class);

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<Page<UserResponse>> listUsers(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(userService.listUsers(pageable));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<Void> updateStatus(@PathVariable UUID id,
                                              @RequestParam String status) {
        log.info("Admin updating user {} status to {}", id, status);
        userService.updateUserStatus(id, status);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/roles")
    public ResponseEntity<UserResponse> assignRole(@PathVariable UUID id,
                                                    @Valid @RequestBody AssignRoleRequest request) {
        log.info("Admin assigning role {} to user {}", request.roleName(), id);
        return ResponseEntity.ok(userService.assignRole(id, request.roleName()));
    }
}
