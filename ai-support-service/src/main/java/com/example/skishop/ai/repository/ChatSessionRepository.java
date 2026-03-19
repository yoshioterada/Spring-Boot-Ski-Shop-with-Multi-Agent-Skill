package com.example.skishop.ai.repository;

import com.example.skishop.ai.model.ChatSession;
import com.example.skishop.ai.model.ChatSession.SessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ChatSessionRepository extends MongoRepository<ChatSession, String> {

    Page<ChatSession> findByUserId(String userId, Pageable pageable);

    List<ChatSession> findByUserIdAndStatus(String userId, SessionStatus status);
}
