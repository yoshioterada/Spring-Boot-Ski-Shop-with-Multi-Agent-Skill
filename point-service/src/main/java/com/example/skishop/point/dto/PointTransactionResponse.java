package com.example.skishop.point.dto;

import com.example.skishop.point.model.PointTransaction.TransactionType;

import java.time.Instant;
import java.util.UUID;

public record PointTransactionResponse(
        UUID id,
        UUID userId,
        TransactionType transactionType,
        int amount,
        int balanceAfter,
        String description,
        String referenceId,
        Instant expiresAt,
        Instant createdAt
) {}
