package com.example.skishop.usermanagement.dto.request;

import jakarta.validation.constraints.Pattern;

public record UpdatePreferencesRequest(
    @Pattern(regexp = "[a-z]{2}", message = "言語コードは2文字の小文字アルファベットで入力してください")
    String language,

    @Pattern(regexp = "[A-Z]{3}", message = "通貨コードは3文字の大文字アルファベットで入力してください")
    String currency
) {}
