package com.example.skishop.usermanagement.dto.response;

import com.example.skishop.usermanagement.model.AddressType;

import java.util.UUID;

public record AddressResponse(
    Long id,
    UUID userId,
    AddressType addressType,
    String recipient,
    String zipCode,
    String prefecture,
    String city,
    String streetAddress,
    String building,
    String phoneNumber,
    boolean isDefault
) {}
