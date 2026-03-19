package com.example.skishop.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new IllegalStateException("No authenticated user in SecurityContext");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UUID uuid) {
            return uuid;
        }
        throw new IllegalStateException("Unexpected principal type: " + principal.getClass().getName());
    }

    public static boolean isCurrentUser(UUID userId) {
        return getCurrentUserId().equals(userId);
    }

    public static boolean hasRole(String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }

    public static void verifyOwnershipOrAdmin(UUID resourceOwnerId) {
        if (!isCurrentUser(resourceOwnerId) && !hasRole("ADMIN")) {
            throw new com.example.skishop.common.exception.AuthorizationDeniedException(
                    "このリソースへのアクセス権がありません");
        }
    }
}
