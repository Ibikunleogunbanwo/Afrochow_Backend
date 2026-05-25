package com.afrochow.outbox.service;

import com.afrochow.outbox.enums.OutboxStatus;
import com.afrochow.outbox.model.OutboxEvent;
import com.afrochow.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Read/publish side of the transactional outbox.
 *
 * Polls for PENDING events, claims them in a short DB transaction, publishes
 * each event to Kafka outside the claim transaction, then records success or
 * retry state in another short transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPoller {

    private static final int BATCH_SIZE = 50;
    private static final int MAX_RETRIES = 3;
    private static final int PROCESSING_TIMEOUT_MINUTES = 10;

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxKafkaPublisher outboxKafkaPublisher;
    private final PlatformTransactionManager transactionManager;

    @Scheduled(fixedDelay = 2000)
    public void poll() {
        List<OutboxEvent> events = claimBatch();

        if (events.isEmpty()) return;

        log.debug("outbox.poll batch={}", events.size());

        for (OutboxEvent event : events) {
            try {
                outboxKafkaPublisher.publish(event);
                markPublished(event.getId());
                log.debug("outbox.published id={} type={} topic={}",
                        event.getId(), event.getEventType(), event.getTopic());
            } catch (Exception ex) {
                int retries = markRetryOrFailed(event.getId(), ex);
                log.warn("outbox.publish_failed id={} type={} retries={}/{}",
                        event.getId(), event.getEventType(), retries, MAX_RETRIES, ex);
            }
        }
    }

    private List<OutboxEvent> claimBatch() {
        return tx().execute(status -> {
            LocalDateTime now = LocalDateTime.now();
            int resetCount = outboxEventRepository.resetStaleProcessingEvents(
                    now.minusMinutes(PROCESSING_TIMEOUT_MINUTES));
            if (resetCount > 0) {
                log.warn("outbox.reset_stale_processing count={}", resetCount);
            }

            List<OutboxEvent> events =
                    outboxEventRepository.findPendingForUpdateSkipLocked(BATCH_SIZE);

            for (OutboxEvent event : events) {
                event.setStatus(OutboxStatus.PROCESSING);
                event.setClaimedAt(now);
            }

            outboxEventRepository.saveAll(events);
            return new ArrayList<>(events);
        });
    }

    private void markPublished(Long eventId) {
        tx().executeWithoutResult(status -> {
            OutboxEvent event = outboxEventRepository.findById(eventId)
                    .orElseThrow(() -> new IllegalStateException("Outbox event not found: " + eventId));
            LocalDateTime now = LocalDateTime.now();
            event.setStatus(OutboxStatus.PROCESSED);
            event.setPublishedAt(now);
            event.setProcessedAt(now);
            event.setClaimedAt(null);
            outboxEventRepository.save(event);
        });
    }

    private int markRetryOrFailed(Long eventId, Exception ex) {
        return tx().execute(status -> {
            OutboxEvent event = outboxEventRepository.findById(eventId)
                    .orElseThrow(() -> new IllegalStateException("Outbox event not found: " + eventId));
            int retries = event.getRetryCount() + 1;
            event.setRetryCount(retries);
            event.setLastError(truncate(ex.getMessage(), 500));
            event.setClaimedAt(null);

            if (retries >= MAX_RETRIES) {
                event.setStatus(OutboxStatus.FAILED);
                log.error("outbox.failed id={} type={} retries={} moved_to=FAILED",
                        event.getId(), event.getEventType(), retries, ex);
            } else {
                event.setStatus(OutboxStatus.PENDING);
            }

            outboxEventRepository.save(event);
            return retries;
        });
    }

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
