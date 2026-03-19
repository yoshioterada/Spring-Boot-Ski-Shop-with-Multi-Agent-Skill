package com.example.skishop.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "メールアドレスは必須です")
        @Email(message = "有効なメールアドレスを入力してください")
        @Size(max = 255)
        String email,

        @NotBlank(message = "パスワードは必須です")
        @Size(min = 8, max = 128, message = "パスワードは8〜128文字で入力してください")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$",
                message = "パスワードは大文字、小文字、数字を含む必要があります")
        String password,

        @NotBlank(message = "名前は必須です")
        @Size(min = 1, max = 100, message = "名前は1〜100文字で入力してください")
        String firstName,

        @NotBlank(message = "姓は必須です")
        @Size(min = 1, max = 100, message = "姓は1〜100文字で入力してください")
        String lastName
) {}
