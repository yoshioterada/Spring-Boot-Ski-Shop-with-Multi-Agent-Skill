package com.example.skishop.ai.repository;

import com.example.skishop.ai.model.LlmAuditLog;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface LlmAuditLogRepository extends MongoRepository<LlmAuditLog, String> {
}
