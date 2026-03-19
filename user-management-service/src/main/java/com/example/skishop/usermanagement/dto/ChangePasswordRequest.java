package com.example.skishop.usermanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "現在のパスワードは必須です")
        String currentPassword,

        @NotBlank(message = "新しいパスワードは必須です")
        @Size(min = 8, max = 128, message = "パスワードは8〜128文字で入力してください")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z0-9]).*$",
                message = "パスワードは大文字、小文字、数字、特殊文字を含む必要があります")
        String newPassword
) {}
