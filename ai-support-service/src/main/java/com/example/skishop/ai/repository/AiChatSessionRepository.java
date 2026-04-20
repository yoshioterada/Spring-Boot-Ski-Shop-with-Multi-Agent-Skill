package com.example.skishop.ai.repository;

import com.example.skishop.ai.model.AiChatSession;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AiChatSessionRepository extends MongoRepository<AiChatSession, String> {
}
