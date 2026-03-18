package com.example.skishop.usermanagement.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserDeletedEvent(
    UUID userId,
    String email,
    OffsetDateTime deletedAt
) {}
