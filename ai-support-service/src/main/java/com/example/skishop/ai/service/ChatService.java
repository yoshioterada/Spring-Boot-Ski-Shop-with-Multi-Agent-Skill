package com.example.skishop.ai.service;

import com.example.skishop.ai.dto.*;
import com.example.skishop.ai.model.ChatMessage;
import com.example.skishop.ai.model.ChatMessage.MessageRole;
import com.example.skishop.ai.model.ChatSession;
import com.example.skishop.ai.model.ChatSession.SessionStatus;
import com.example.skishop.ai.repository.ChatSessionRepository;
import com.example.skishop.common.exception.BusinessRuleViolationException;
import com.example.skishop.common.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private static final String SYSTEM_PROMPT = """
            You are a helpful customer support assistant for a ski shop e-commerce platform.
            You can help customers with:
            - Product information about ski equipment, snowboards, boots, and accessories
            - Order status and tracking
            - Returns and exchanges
            - Size and fit recommendations
            - General ski and snowboard advice
            
            Be friendly, professional, and concise in your responses.
            If you don't know the answer, suggest the customer contact human support.
            Always respond in the same language as the customer's message.
            """;

    private static final int MAX_HISTORY_MESSAGES = 20;

    private final ChatSessionRepository chatSessionRepository;
    private final ChatClient chatClient;

    public ChatService(ChatSessionRepository chatSessionRepository,
                       ChatClient.Builder chatClientBuilder) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatClient = chatClientBuilder.build();
    }

    public ChatSessionResponse createSession(CreateChatSessionRequest request) {
        ChatSession session = new ChatSession(request.userId(), request.sessionType());
        session = chatSessionRepository.save(session);

        log.info("Created chat session {} for user {}", session.getId(), request.userId());
        return toSessionResponse(session);
    }

    public ChatMessageResponse sendMessage(ChatMessageRequest request) {
        ChatSession session = chatSessionRepository.findById(request.sessionId())
                .orElseThrow(() -> new ResourceNotFoundException("ChatSession",
                        request.sessionId()));

        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new BusinessRuleViolationException("CHAT-4221",
                    "Chat session is not active: " + session.getStatus());
        }

        ChatMessage userMessage = new ChatMessage(MessageRole.USER, request.message());
        session.addMessage(userMessage);

        List<Message> promptMessages = buildPromptMessages(session);
        String aiResponse = chatClient.prompt(new Prompt(promptMessages))
                .call()
                .content();

        ChatMessage assistantMessage = new ChatMessage(MessageRole.ASSISTANT, aiResponse);
        session.addMessage(assistantMessage);
        chatSessionRepository.save(session);

        log.info("Processed message in session {}", session.getId());

        return new ChatMessageResponse(
                session.getId(),
                assistantMessage.getId(),
                aiResponse,
                MessageRole.ASSISTANT.name()
        );
    }

    public ChatSessionResponse getSession(String sessionId) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ChatSession",
                        sessionId));
        return toSessionResponse(session);
    }

    public Page<ChatSessionResponse> getUserSessions(String userId, Pageable pageable) {
        return chatSessionRepository.findByUserId(userId, pageable)
                .map(this::toSessionResponse);
    }

    public ChatSessionResponse updateSessionStatus(String sessionId, SessionStatus status) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ChatSession",
                        sessionId));

        session.setStatus(status);
        session = chatSessionRepository.save(session);
        return toSessionResponse(session);
    }

    public FeedbackResponse submitFeedback(ChatFeedbackRequest request) {
        ChatSession session = chatSessionRepository.findById(request.sessionId())
                .orElseThrow(() -> new ResourceNotFoundException("ChatSession",
                        request.sessionId()));

        session.setSatisfactionScore(request.satisfactionScore());
        chatSessionRepository.save(session);

        log.info("Recorded feedback for session {}: score={}", request.sessionId(), request.satisfactionScore());
        return new FeedbackResponse(UUID.randomUUID().toString(), "RECEIVED", "Feedback recorded successfully");
    }

    public EscalationResponse escalateSession(EscalationRequest request) {
        ChatSession session = chatSessionRepository.findById(request.sessionId())
                .orElseThrow(() -> new ResourceNotFoundException("ChatSession",
                        request.sessionId()));

        session.setStatus(SessionStatus.ESCALATED);
        if (request.priority() != null) {
            session.setPriority(request.priority());
        }
        chatSessionRepository.save(session);

        log.info("Escalated session {} to human agent", request.sessionId());
        return new EscalationResponse(session.getId(), "ESCALATED", session.getAssignedAgent(), java.time.Instant.now());
    }

    public IntentsResponse getSupportedIntents() {
        List<IntentsResponse.IntentInfo> intents = List.of(
                new IntentsResponse.IntentInfo("PRODUCT_INQUIRY",
                        "Product information inquiries",
                        List.of("Tell me about ski boots", "What skis do you recommend?")),
                new IntentsResponse.IntentInfo("ORDER_STATUS",
                        "Order status tracking",
                        List.of("Where is my order?", "Track my package")),
                new IntentsResponse.IntentInfo("RETURN_EXCHANGE",
                        "Returns and exchanges",
                        List.of("I want to return an item", "Exchange my order")),
                new IntentsResponse.IntentInfo("SIZE_RECOMMENDATION",
                        "Size and fit recommendations",
                        List.of("What size ski boots should I get?", "Help me find the right size")),
                new IntentsResponse.IntentInfo("GENERAL_SUPPORT",
                        "General customer support",
                        List.of("I need help", "Contact support"))
        );

        return new IntentsResponse(intents);
    }

    private List<Message> buildPromptMessages(ChatSession session) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));

        List<ChatMessage> history = session.getMessages();
        if (history.size() > MAX_HISTORY_MESSAGES) {
            history = history.subList(history.size() - MAX_HISTORY_MESSAGES, history.size());
        }

        for (ChatMessage msg : history) {
            switch (msg.getRole()) {
                case USER -> messages.add(new UserMessage(msg.getContent()));
                case ASSISTANT -> messages.add(new AssistantMessage(msg.getContent()));
                case SYSTEM -> messages.add(new SystemMessage(msg.getContent()));
            }
        }
        return messages;
    }

    private ChatSessionResponse toSessionResponse(ChatSession session) {
        return new ChatSessionResponse(
                session.getId(),
                session.getUserId(),
                session.getSessionType(),
                session.getStatus(),
                session.getMessages(),
                session.getCreatedAt(),
                session.getLastActivity()
        );
    }
}
