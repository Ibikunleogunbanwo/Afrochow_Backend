package com.afrochow.address.service;

import com.afrochow.kafka.service.ProcessedKafkaEventService;
import com.afrochow.outbox.enums.OutboxEventType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.kafka.consumers.address-geocoding.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class AddressGeocodingEventConsumer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AddressService addressService;
    private final ProcessedKafkaEventService processedKafkaEventService;

    @Value("${app.kafka.consumers.address-geocoding.group-id:afrochow-address-geocoding-service}")
    private String consumerName;

    @KafkaListener(
            topics = "${app.kafka.topics.domain-events:afrochow.domain-events}",
            groupId = "${app.kafka.consumers.address-geocoding.group-id:afrochow-address-geocoding-service}"
    )
    public void consume(
            @Payload String payload,
            @Header("outbox-id") String outboxId,
            @Header("outbox-event-id") String eventId,
            @Header("outbox-event-type") String eventType,
            Acknowledgment acknowledgment
    ) throws Exception {
        OutboxEventType type = OutboxEventType.valueOf(eventType);
        if (type != OutboxEventType.ADDRESS_GEOCODING_REQUESTED) {
            acknowledgment.acknowledge();
            return;
        }

        if (processedKafkaEventService.alreadyProcessed(consumerName, eventId)) {
            log.info("address.geocoding.consumer.duplicate_skipped outboxId={} eventId={}", outboxId, eventId);
            acknowledgment.acknowledge();
            return;
        }

        Map<String, String> p = parse(payload);
        String publicAddressId = required(p, "publicAddressId", eventId);

        log.info("address.geocoding.consumer.received outboxId={} eventId={} publicAddressId={}",
                outboxId, eventId, publicAddressId);

        addressService.geocodeAddress(publicAddressId);
        processedKafkaEventService.markProcessed(consumerName, eventId, outboxId, eventType);
        acknowledgment.acknowledge();

        log.info("address.geocoding.consumer.acked outboxId={} eventId={} publicAddressId={}",
                outboxId, eventId, publicAddressId);
    }

    private Map<String, String> parse(String payload) throws Exception {
        return MAPPER.readValue(payload, new TypeReference<Map<String, String>>() {});
    }

    private String required(Map<String, String> payload, String key, String eventId) {
        String value = payload.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing event payload field '" + key + "' for address geocoding eventId=" + eventId);
        }
        return value;
    }
}
