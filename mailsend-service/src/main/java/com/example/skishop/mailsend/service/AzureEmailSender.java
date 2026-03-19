package com.example.skishop.mailsend.service;

import com.azure.communication.email.EmailClient;
import com.azure.communication.email.models.EmailMessage;
import com.azure.communication.email.models.EmailSendResult;
import com.azure.core.exception.HttpResponseException;
import com.azure.core.util.polling.PollResponse;
import com.azure.core.util.polling.SyncPoller;
import com.example.skishop.mailsend.config.AzureCommunicationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AzureEmailSender {

    private static final Duration DEFAULT_POLL_TIMEOUT = Duration.ofSeconds(30);

    private final EmailClient emailClient;
    private final AzureCommunicationProperties props;

    public AzureEmailSender(EmailClient emailClient, AzureCommunicationProperties props) {
        this.emailClient = emailClient;
        this.props = props;
    }

    public String send(String recipientEmail, String subject, String htmlBody, String plainTextBody) {
        try {
            EmailMessage message = new EmailMessage()
                    .setSenderAddress(props.senderAddress())
                    .setToRecipients(recipientEmail)
                    .setSubject(subject)
                    .setBodyHtml(htmlBody)
                    .setBodyPlainText(plainTextBody);

            SyncPoller<EmailSendResult, EmailSendResult> poller = emailClient.beginSend(message);
            PollResponse<EmailSendResult> response = poller.waitForCompletion(DEFAULT_POLL_TIMEOUT);
            EmailSendResult result = response.getValue();
            return result != null ? result.getId() : null;
        } catch (HttpResponseException e) {
            Integer statusCode = e.getResponse() != null ? e.getResponse().getStatusCode() : null;
            Long retryAfterMs = null;
            if (e.getResponse() != null && statusCode != null && statusCode == 429) {
                String retryAfter = e.getResponse().getHeaders().getValue("Retry-After");
                retryAfterMs = parseRetryAfterMs(retryAfter);
            }
            throw new EmailSendException("Failed to send email via Azure ACS", e, statusCode, retryAfterMs);
        } catch (Exception e) {
            throw new EmailSendException("Failed to send email via Azure ACS", e, null, null);
        }
    }

    private static Long parseRetryAfterMs(String retryAfterHeader) {
        if (retryAfterHeader == null || retryAfterHeader.isBlank()) {
            return null;
        }
        try {
            long seconds = Long.parseLong(retryAfterHeader.trim());
            return seconds * 1000L;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
