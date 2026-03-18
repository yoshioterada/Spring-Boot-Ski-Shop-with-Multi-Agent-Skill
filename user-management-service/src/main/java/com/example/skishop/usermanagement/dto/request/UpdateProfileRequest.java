package com.example.skishop.usermanagement.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateProfileRequest(
    @NotBlank(message = "名は必須です")
    @Size(min = 1, max = 50, message = "名は1〜50文字で入力してください")
    String firstName,

    @NotBlank(message = "姓は必須です")
    @Size(min = 1, max = 50, message = "姓は1〜50文字で入力してください")
    String lastName,

    @Pattern(regexp = "^[0-9]{10,11}$|^$", message = "電話番号は10〜11桁の数字で入力してください")
    String phoneNumber,

    LocalDate birthDate
) {}
