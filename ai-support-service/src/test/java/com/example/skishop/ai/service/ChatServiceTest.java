package com.example.skishop.ai.service;

import com.example.skishop.ai.dto.*;
import com.example.skishop.ai.model.ChatMessage.MessageRole;
import com.example.skishop.ai.model.ChatSession;
import com.example.skishop.ai.model.ChatSession.SessionStatus;
import com.example.skishop.ai.model.ChatSession.SessionType;
import com.example.skishop.ai.repository.ChatSessionRepository;
import com.example.skishop.common.exception.BusinessRuleViolationException;
import com.example.skishop.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec chatClientRequestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        when(chatClientBuilder.build()).thenReturn(chatClient);
        chatService = new ChatService(chatSessionRepository, chatClientBuilder);
    }

    @Test
    @DisplayName("有効なリクエストでチャットセッションが作成される")
    void should_createSession_when_validRequest() {
        // Arrange
        CreateChatSessionRequest request = new CreateChatSessionRequest("user-123", SessionType.SUPPORT);
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        ChatSessionResponse response = chatService.createSession(request);

        // Assert
        assertThat(response.userId()).isEqualTo("user-123");
        assertThat(response.sessionType()).isEqualTo(SessionType.SUPPORT);
        assertThat(response.status()).isEqualTo(SessionStatus.ACTIVE);
    }

    @Test
    @DisplayName("存在しないセッションへのメッセージ送信で例外がスローされる")
    void should_throwException_when_sessionNotFound() {
        // Arrange
        ChatMessageRequest request = new ChatMessageRequest("nonexistent", "Hello");
        when(chatSessionRepository.findById("nonexistent")).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> chatService.sendMessage(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("非アクティブセッションへのメッセージ送信で例外がスローされる")
    void should_throwException_when_sessionInactive() {
        // Arrange
        ChatSession session = new ChatSession("user-123", SessionType.SUPPORT);
        session.setStatus(SessionStatus.COMPLETED);
        when(chatSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        ChatMessageRequest request = new ChatMessageRequest(session.getId(), "Hello");

        // Act & Assert
        assertThatThrownBy(() -> chatService.sendMessage(request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("not active");
    }

    @Test
    @DisplayName("アクティブセッションでAIレスポンスが返される")
    void should_returnAiResponse_when_activeSession() {
        // Arrange
        ChatSession session = new ChatSession("user-123", SessionType.SUPPORT);
        when(chatSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(inv -> inv.getArgument(0));
        when(chatClient.prompt(any(Prompt.class))).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("I can help with ski equipment!");

        // Act
        ChatMessageRequest request = new ChatMessageRequest(session.getId(), "Tell me about ski boots");
        ChatMessageResponse response = chatService.sendMessage(request);

        // Assert
        assertThat(response.content()).isEqualTo("I can help with ski equipment!");
        assertThat(response.role()).isEqualTo(MessageRole.ASSISTANT.name());
    }

    @Test
    @DisplayName("有効なセッションのステータス更新が成功する")
    void should_updateStatus_when_validSession() {
        // Arrange
        ChatSession session = new ChatSession("user-123", SessionType.SUPPORT);
        when(chatSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        ChatSessionResponse response = chatService.updateSessionStatus(session.getId(), SessionStatus.COMPLETED);

        // Assert
        assertThat(response.status()).isEqualTo(SessionStatus.COMPLETED);
    }

    @Test
    @DisplayName("チャットフィードバックが正常に記録される")
    void should_recordFeedback_when_validSession() {
        // Arrange
        ChatSession session = new ChatSession("user-123", SessionType.SUPPORT);
        when(chatSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(inv -> inv.getArgument(0));
        ChatFeedbackRequest request = new ChatFeedbackRequest(session.getId(), 4.5, "Great service!");

        // Act
        FeedbackResponse response = chatService.submitFeedback(request);

        // Assert
        assertThat(response.status()).isEqualTo("RECEIVED");
        verify(chatSessionRepository).save(any(ChatSession.class));
    }

    @Test
    @DisplayName("存在しないセッションへのフィードバックで例外がスローされる")
    void should_throwException_when_feedbackSessionNotFound() {
        // Arrange
        ChatFeedbackRequest request = new ChatFeedbackRequest("nonexistent", 4.0, "comment");
        when(chatSessionRepository.findById("nonexistent")).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> chatService.submitFeedback(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("セッションエスカレーションが正常に処理される")
    void should_escalateSession_when_validSession() {
        // Arrange
        ChatSession session = new ChatSession("user-123", SessionType.SUPPORT);
        when(chatSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(inv -> inv.getArgument(0));
        EscalationRequest request = new EscalationRequest(session.getId(), "Complex issue", "HIGH");

        // Act
        EscalationResponse response = chatService.escalateSession(request);

        // Assert
        assertThat(response.status()).isEqualTo("ESCALATED");
        assertThat(response.sessionId()).isEqualTo(session.getId());
    }

    @Test
    @DisplayName("サポートインテント一覧が取得できる")
    void should_returnIntents_when_requested() {
        // Act
        IntentsResponse response = chatService.getSupportedIntents();

        // Assert
        assertThat(response.intents()).isNotEmpty();
        assertThat(response.intents()).hasSizeGreaterThanOrEqualTo(5);
    }
}
