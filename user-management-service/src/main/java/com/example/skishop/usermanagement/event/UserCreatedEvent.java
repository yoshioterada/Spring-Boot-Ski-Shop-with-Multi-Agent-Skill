package com.example.skishop.usermanagement.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserCreatedEvent(
    UUID userId,
    String email,
    String firstName,
    String lastName,
    OffsetDateTime createdAt
) {}
