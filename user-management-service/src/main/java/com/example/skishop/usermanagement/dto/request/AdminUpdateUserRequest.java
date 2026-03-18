package com.example.skishop.usermanagement.dto.request;

import com.example.skishop.usermanagement.model.UserStatus;

public record AdminUpdateUserRequest(
    String firstName,
    String lastName,
    String phoneNumber,
    UserStatus status
) {}
