package com.example.skishop.authentication.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "ユーザー名は必須です")
    @Size(min = 3, max = 50, message = "ユーザー名は3〜50文字で入力してください")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "ユーザー名は英数字・ハイフン・アンダースコアのみ使用可能です")
    String username,

    @NotBlank(message = "メールアドレスは必須です")
    @Email(message = "有効なメールアドレスを入力してください")
    @Size(max = 255)
    String email,

    @NotBlank(message = "パスワードは必須です")
    @Size(min = 8, max = 100, message = "パスワードは8〜100文字で入力してください")
    String password
) {}
