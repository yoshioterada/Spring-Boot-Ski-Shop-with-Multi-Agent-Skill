package com.example.skishop.usermanagement.dto;

import jakarta.validation.constraints.NotBlank;

public record AssignRoleRequest(
        @NotBlank(message = "ロール名は必須です")
        String roleName
) {}
