package com.example.skishop.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetRequest(
        @NotBlank(message = "メールアドレスは必須です")
        @Email(message = "有効なメールアドレスを入力してください")
        @Size(max = 255)
        String email
) {}
