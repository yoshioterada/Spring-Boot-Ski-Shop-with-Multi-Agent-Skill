package com.example.skishop.ai.dto;

import java.util.List;

public record IntentsResponse(
        List<IntentInfo> intents
) {
    public record IntentInfo(
            String name,
            String description,
            List<String> examplePhrases
    ) {}
}
