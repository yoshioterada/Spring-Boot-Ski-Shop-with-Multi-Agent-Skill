package com.example.skishop.payment.repository;

import com.example.skishop.payment.model.PaymentWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, UUID> {

    Optional<PaymentWebhookEvent> findByProviderAndEventId(String provider, String eventId);
}
