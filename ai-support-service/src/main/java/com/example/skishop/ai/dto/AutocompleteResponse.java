package com.example.skishop.ai.dto;

import java.util.List;

public record AutocompleteResponse(
        String query,
        List<String> suggestions
) {}
