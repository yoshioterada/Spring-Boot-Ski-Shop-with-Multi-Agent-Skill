package com.example.skishop.ai.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record CustomerSegmentResponse(
        String segmentType,
        List<SegmentInfo> segments,
        DataAvailability availability,
        Instant analyzedAt
) {
    public CustomerSegmentResponse(String segmentType, List<SegmentInfo> segments, Instant analyzedAt) {
        this(segmentType, segments, DataAvailability.available(), analyzedAt);
    }

    public record SegmentInfo(
            String segmentId,
            String name,
            int userCount,
            Map<String, Object> characteristics
    ) {}
}
