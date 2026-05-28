package com.example.skishop.ai.dto;

import java.util.List;

public record DataAvailability(
        boolean dataAvailable,
        boolean partial,
        List<String> missingSources
) {
    public static DataAvailability available() {
        return new DataAvailability(true, false, List.of());
    }

    public static DataAvailability partial(List<String> missingSources) {
        return new DataAvailability(true, true, missingSources == null ? List.of() : missingSources);
    }

    public static DataAvailability unavailable(List<String> missingSources) {
        return new DataAvailability(false, false, missingSources == null ? List.of() : missingSources);
    }
}
