package com.example.skishop.ai.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record CustomerSegmentResponse(
        String segmentType,
        List<SegmentInfo> segments,
        Instant analyzedAt
) {
    public record SegmentInfo(
            String segmentId,
            String name,
            int userCount,
            Map<String, Object> characteristics
    ) {}
}
