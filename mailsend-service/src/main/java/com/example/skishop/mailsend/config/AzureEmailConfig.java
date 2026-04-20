package com.example.skishop.mailsend.config;

import com.azure.communication.email.EmailClient;
import com.azure.communication.email.EmailClientBuilder;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.example.skishop.mailsend.consumer.MailEventConsumer;
import com.example.skishop.mailsend.service.MailService;
import com.example.skishop.mailsend.service.UserInfoResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AzureEmailConfig {

    @Bean
    public EmailClient emailClient(AzureCommunicationProperties props) {
        var builder = new EmailClientBuilder();

        if (props.connectionString() != null && !props.connectionString().isBlank()) {
            return builder.connectionString(props.connectionString()).buildClient();
        }

        return builder
                .endpoint(props.endpoint())
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient();
    }

    @Bean
    public MailEventConsumer mailEventConsumer(ObjectMapper objectMapper,
                                               MailService mailService,
                                               UserInfoResolver userInfoResolver,
                                               MailProperties mailProperties) {
        return new MailEventConsumer(objectMapper, mailService, userInfoResolver, mailProperties);
    }
}
