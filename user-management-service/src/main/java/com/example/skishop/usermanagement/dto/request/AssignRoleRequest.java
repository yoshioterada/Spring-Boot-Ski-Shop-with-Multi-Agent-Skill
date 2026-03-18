package com.example.skishop.usermanagement.dto.request;

import jakarta.validation.constraints.NotNull;

public record AssignRoleRequest(
    @NotNull(message = "ロールIDは必須です")
    Long roleId
) {}
