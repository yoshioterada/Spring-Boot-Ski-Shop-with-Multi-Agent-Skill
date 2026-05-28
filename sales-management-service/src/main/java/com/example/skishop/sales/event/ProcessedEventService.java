package com.example.skishop.sales.event;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ProcessedEventService {

    private static final String INSERT_PROCESSING = """
            INSERT INTO processed_events (event_id, event_type, consumer_name, status)
            VALUES (?, ?, ?, 'PROCESSING')
            """;
    private static final String MARK_PROCESSED = """
            UPDATE processed_events
            SET status = 'PROCESSED', processed_at = NOW(), last_error = NULL
            WHERE event_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public ProcessedEventService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean tryStart(String eventId, String eventType, String consumerName) {
        try {
            jdbcTemplate.update(INSERT_PROCESSING, eventId, eventType, consumerName);
            return true;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }

    public void markProcessed(String eventId) {
        jdbcTemplate.update(MARK_PROCESSED, eventId);
    }
}