package com.example.skishop.mailsend.service;

import com.azure.communication.email.EmailClient;
import com.azure.communication.email.models.EmailMessage;
import com.azure.communication.email.models.EmailSendResult;
import com.azure.core.exception.HttpResponseException;
import com.azure.core.http.HttpHeaders;
import com.azure.core.http.HttpResponse;
import com.azure.core.util.polling.LongRunningOperationStatus;
import com.azure.core.util.polling.PollResponse;
import com.azure.core.util.polling.SyncPoller;
import com.example.skishop.mailsend.config.AzureCommunicationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AzureEmailSenderTest {

    @Mock
    private EmailClient emailClient;

    @Mock
    private AzureCommunicationProperties props;

    private AzureEmailSender azureEmailSender;

    @BeforeEach
    void setUp() {
        when(props.senderAddress()).thenReturn("no-reply@example.com");
        azureEmailSender = new AzureEmailSender(emailClient, props);
    }

    @Test
    @DisplayName("送信成功時、オペレーションIDを返す")
    @SuppressWarnings("unchecked")
    void should_returnOperationId_when_sendSucceeds() {
        // Arrange
        var expectedId = "op-12345";
        var sendResult = mock(EmailSendResult.class);
        when(sendResult.getId()).thenReturn(expectedId);

        var pollResponse = mock(PollResponse.class);
        when(pollResponse.getValue()).thenReturn(sendResult);

        var poller = mock(SyncPoller.class);
        when(poller.waitForCompletion(any())).thenReturn(pollResponse);
        when(emailClient.beginSend(any(EmailMessage.class))).thenReturn(poller);

        // Act
        var result = azureEmailSender.send(
                "user@example.com", "テスト件名", "<p>本文</p>", "本文");

        // Assert
        assertThat(result).isEqualTo(expectedId);

        var captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailClient).beginSend(captor.capture());
        var capturedMessage = captor.getValue();
        assertThat(capturedMessage.getSenderAddress()).isEqualTo("no-reply@example.com");
        assertThat(capturedMessage.getSubject()).isEqualTo("テスト件名");
    }

    @Test
    @DisplayName("HTTP 429エラー時、Retry-Afterヘッダーを解析しリトライ可能な例外をスローする")
    void should_throwRetryableException_when_httpStatus429() {
        // Arrange
        var httpResponse = mock(HttpResponse.class);
        when(httpResponse.getStatusCode()).thenReturn(429);
        var headers = new HttpHeaders().set("Retry-After", "120");
        when(httpResponse.getHeaders()).thenReturn(headers);

        var httpException = new HttpResponseException("Too Many Requests", httpResponse);
        when(emailClient.beginSend(any(EmailMessage.class))).thenThrow(httpException);

        // Act & Assert
        assertThatThrownBy(() -> azureEmailSender.send(
                "user@example.com", "件名", "<p>本文</p>", "本文"))
                .isInstanceOf(EmailSendException.class)
                .hasMessageContaining("Failed to send email via Azure ACS")
                .satisfies(thrown -> {
                    var ex = (EmailSendException) thrown;
                    assertThat(ex.statusCode()).isEqualTo(429);
                    assertThat(ex.retryAfterMs()).isEqualTo(120_000L);
                    assertThat(ex.isRetryable()).isTrue();
                });
    }

    @Test
    @DisplayName("HTTP 500エラー時、リトライ可能な例外をスローする")
    void should_throwRetryableException_when_httpStatus500() {
        // Arrange
        var httpResponse = mock(HttpResponse.class);
        when(httpResponse.getStatusCode()).thenReturn(500);

        var httpException = new HttpResponseException("Internal Server Error", httpResponse);
        when(emailClient.beginSend(any(EmailMessage.class))).thenThrow(httpException);

        // Act & Assert
        assertThatThrownBy(() -> azureEmailSender.send(
                "user@example.com", "件名", "<p>本文</p>", "本文"))
                .isInstanceOf(EmailSendException.class)
                .satisfies(thrown -> {
                    var ex = (EmailSendException) thrown;
                    assertThat(ex.statusCode()).isEqualTo(500);
                    assertThat(ex.retryAfterMs()).isNull();
                    assertThat(ex.isRetryable()).isTrue();
                });
    }

    @Test
    @DisplayName("HTTP 400エラー時、リトライ不可の例外をスローする")
    void should_throwNonRetryableException_when_httpStatus400() {
        // Arrange
        var httpResponse = mock(HttpResponse.class);
        when(httpResponse.getStatusCode()).thenReturn(400);

        var httpException = new HttpResponseException("Bad Request", httpResponse);
        when(emailClient.beginSend(any(EmailMessage.class))).thenThrow(httpException);

        // Act & Assert
        assertThatThrownBy(() -> azureEmailSender.send(
                "user@example.com", "件名", "<p>本文</p>", "本文"))
                .isInstanceOf(EmailSendException.class)
                .satisfies(thrown -> {
                    var ex = (EmailSendException) thrown;
                    assertThat(ex.statusCode()).isEqualTo(400);
                    assertThat(ex.retryAfterMs()).isNull();
                    assertThat(ex.isRetryable()).isFalse();
                });
    }

    @Test
    @DisplayName("HTTP以外の一般例外発生時、statusCodeがnullでリトライ可能な例外をスローする")
    void should_throwRetryableException_when_genericExceptionOccurs() {
        // Arrange
        when(emailClient.beginSend(any(EmailMessage.class)))
                .thenThrow(new RuntimeException("Connection timeout"));

        // Act & Assert
        assertThatThrownBy(() -> azureEmailSender.send(
                "user@example.com", "件名", "<p>本文</p>", "本文"))
                .isInstanceOf(EmailSendException.class)
                .hasMessageContaining("Failed to send email via Azure ACS")
                .satisfies(thrown -> {
                    var ex = (EmailSendException) thrown;
                    assertThat(ex.statusCode()).isNull();
                    assertThat(ex.retryAfterMs()).isNull();
                    assertThat(ex.isRetryable()).isTrue();
                });
    }

    @Test
    @DisplayName("ポーリング結果のvalueがnullの場合、nullを返す")
    @SuppressWarnings("unchecked")
    void should_returnNull_when_pollResultValueIsNull() {
        // Arrange
        var pollResponse = mock(PollResponse.class);
        when(pollResponse.getValue()).thenReturn(null);

        var poller = mock(SyncPoller.class);
        when(poller.waitForCompletion(any())).thenReturn(pollResponse);
        when(emailClient.beginSend(any(EmailMessage.class))).thenReturn(poller);

        // Act
        var result = azureEmailSender.send(
                "user@example.com", "件名", "<p>本文</p>", "本文");

        // Assert
        assertThat(result).isNull();
    }
}
