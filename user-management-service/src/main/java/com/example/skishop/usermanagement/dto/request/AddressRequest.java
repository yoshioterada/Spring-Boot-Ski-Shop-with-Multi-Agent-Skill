package com.example.skishop.usermanagement.dto.request;

import com.example.skishop.usermanagement.model.AddressType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressRequest(
    @NotNull(message = "住所タイプは必須です")
    AddressType addressType,

    @NotBlank(message = "受取人名は必須です")
    @Size(min = 1, max = 100, message = "受取人名は1〜100文字で入力してください")
    String recipient,

    @Pattern(regexp = "\\d{3}-\\d{4}", message = "郵便番号はXXX-XXXX形式で入力してください")
    String zipCode,

    @NotBlank(message = "都道府県は必須です")
    String prefecture,

    @NotBlank(message = "市区町村は必須です")
    String city,

    @NotBlank(message = "番地は必須です")
    String streetAddress,

    String building,

    @Pattern(regexp = "^[0-9]{10,11}$|^$", message = "電話番号は10〜11桁の数字で入力してください")
    String phoneNumber,

    boolean isDefault
) {}
