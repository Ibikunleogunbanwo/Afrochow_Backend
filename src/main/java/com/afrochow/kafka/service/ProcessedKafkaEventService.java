package com.afrochow.kafka.service;

import com.afrochow.kafka.model.ProcessedKafkaEvent;
import com.afrochow.kafka.repository.ProcessedKafkaEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessedKafkaEventService {

    private final ProcessedKafkaEventRepository repository;

    @Transactional(readOnly = true)
    public boolean alreadyProcessed(String consumerName, String eventId) {
        return repository.existsByConsumerNameAndEventId(consumerName, eventId);
    }

    @Transactional
    public boolean markProcessed(String consumerName, String eventId, String outboxId, String eventType) {
        try {
            repository.save(ProcessedKafkaEvent.builder()
                    .consumerName(consumerName)
                    .eventId(eventId)
                    .outboxId(outboxId)
                    .eventType(eventType)
                    .build());
            return true;
        } catch (DataIntegrityViolationException ex) {
            log.info("kafka.event.already_marked_processed consumer={} eventId={}", consumerName, eventId);
            return false;
        }
    }
}
