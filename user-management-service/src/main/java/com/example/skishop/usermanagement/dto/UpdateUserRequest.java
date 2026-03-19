package com.example.skishop.usermanagement.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdateUserRequest(
        @Size(min = 1, max = 100)
        String firstName,

        @Size(min = 1, max = 100)
        String lastName,

        @Size(max = 20)
        String phoneNumber,

        LocalDate birthDate,

        String gender
) {}
