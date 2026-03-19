package com.example.skishop.authentication.dto;

import java.util.List;

public record UserInfoResponse(
    String id,
    String username,
    String email,
    List<String> roles,
    boolean enabled
) {
    public static UserInfoResponse from(com.example.skishop.authentication.entity.User user) {
        return new UserInfoResponse(
            user.getId().toString(),
            user.getUsername(),
            user.getEmail(),
            user.getRoles().stream().sorted().toList(),
            user.isEnabled()
        );
    }
}
