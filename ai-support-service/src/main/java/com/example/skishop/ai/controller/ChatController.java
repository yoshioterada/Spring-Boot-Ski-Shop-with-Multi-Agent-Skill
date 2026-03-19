package com.example.skishop.ai.controller;

import com.example.skishop.ai.dto.*;
import com.example.skishop.ai.model.ChatSession.SessionStatus;
import com.example.skishop.ai.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/session")
    public ResponseEntity<ChatSessionResponse> createSession(
            @Valid @RequestBody CreateChatSessionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(chatService.createSession(request));
    }

    @PostMapping("/message")
    public ResponseEntity<ChatMessageResponse> sendMessage(
            @Valid @RequestBody ChatMessageRequest request) {
        return ResponseEntity.ok(chatService.sendMessage(request));
    }

    @GetMapping("/history/{sessionId}")
    public ResponseEntity<ChatSessionResponse> getSession(@PathVariable String sessionId) {
        return ResponseEntity.ok(chatService.getSession(sessionId));
    }

    @GetMapping("/sessions/{userId}")
    public ResponseEntity<Page<ChatSessionResponse>> getUserSessions(
            @PathVariable String userId, @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(chatService.getUserSessions(userId, pageable));
    }

    @PutMapping("/session/{sessionId}/status")
    public ResponseEntity<ChatSessionResponse> updateSessionStatus(
            @PathVariable String sessionId, @RequestParam SessionStatus status) {
        return ResponseEntity.ok(chatService.updateSessionStatus(sessionId, status));
    }

    @PostMapping("/feedback")
    public ResponseEntity<FeedbackResponse> submitFeedback(
            @Valid @RequestBody ChatFeedbackRequest request) {
        return ResponseEntity.ok(chatService.submitFeedback(request));
    }

    @PostMapping("/escalate")
    public ResponseEntity<EscalationResponse> escalateSession(
            @Valid @RequestBody EscalationRequest request) {
        return ResponseEntity.ok(chatService.escalateSession(request));
    }

    @GetMapping("/intents")
    public ResponseEntity<IntentsResponse> getSupportedIntents() {
        return ResponseEntity.ok(chatService.getSupportedIntents());
    }
}
