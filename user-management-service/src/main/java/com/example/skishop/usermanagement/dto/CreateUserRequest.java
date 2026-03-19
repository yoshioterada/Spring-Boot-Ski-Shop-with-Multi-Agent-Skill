package com.example.skishop.usermanagement.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record CreateUserRequest(
        @NotBlank(message = "メールアドレスは必須です")
        @Email(message = "有効なメールアドレスを入力してください")
        @Size(max = 255)
        String email,

        @NotBlank(message = "パスワードは必須です")
        @Size(min = 8, max = 128, message = "パスワードは8〜128文字で入力してください")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z0-9]).*$",
                message = "パスワードは大文字、小文字、数字、特殊文字を含む必要があります")
        String password,

        @NotBlank(message = "名前は必須です")
        @Size(min = 1, max = 100)
        String firstName,

        @NotBlank(message = "姓は必須です")
        @Size(min = 1, max = 100)
        String lastName,

        @Size(max = 20)
        String phoneNumber,

        LocalDate birthDate,

        String gender
) {}
