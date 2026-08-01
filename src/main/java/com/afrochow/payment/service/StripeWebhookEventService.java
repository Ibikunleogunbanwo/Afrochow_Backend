package com.afrochow.payment.service;

import com.afrochow.payment.model.StripeWebhookEvent;
import com.afrochow.payment.repository.StripeWebhookEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class StripeWebhookEventService {

    private static final String PROCESSING = "PROCESSING";
    private static final String PROCESSED = "PROCESSED";

    private final StripeWebhookEventRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claim(String eventId, String eventType) {
        try {
            repository.saveAndFlush(StripeWebhookEvent.builder()
                    .eventId(eventId)
                    .eventType(eventType)
                    .status(PROCESSING)
                    .createdAt(LocalDateTime.now())
                    .build());
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessed(String eventId) {
        repository.findById(eventId).ifPresent(event -> {
            event.setStatus(PROCESSED);
            event.setProcessedAt(LocalDateTime.now());
            event.setLastError(null);
            repository.save(event);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseClaim(String eventId, String errorMessage) {
        repository.findById(eventId).ifPresent(event -> {
            event.setStatus("FAILED");
            event.setLastError(errorMessage);
            repository.delete(event);
        });
    }
}
