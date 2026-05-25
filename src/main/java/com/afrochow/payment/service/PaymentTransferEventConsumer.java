package com.afrochow.payment.service;

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
        name = "app.kafka.consumers.payment-transfer.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class PaymentTransferEventConsumer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final PaymentService paymentService;
    private final ProcessedKafkaEventService processedKafkaEventService;

    @Value("${app.kafka.consumers.payment-transfer.group-id:afrochow-payment-transfer-service}")
    private String consumerName;

    @KafkaListener(
            topics = "${app.kafka.topics.domain-events:afrochow.domain-events}",
            groupId = "${app.kafka.consumers.payment-transfer.group-id:afrochow-payment-transfer-service}"
    )
    public void consume(
            @Payload String payload,
            @Header("outbox-id") String outboxId,
            @Header("outbox-event-id") String eventId,
            @Header("outbox-event-type") String eventType,
            Acknowledgment acknowledgment
    ) throws Exception {
        OutboxEventType type = OutboxEventType.valueOf(eventType);
        if (type != OutboxEventType.PAYMENT_TRANSFER_REQUESTED) {
            acknowledgment.acknowledge();
            return;
        }

        if (processedKafkaEventService.alreadyProcessed(consumerName, eventId)) {
            log.info("payment.transfer.consumer.duplicate_skipped outboxId={} eventId={}", outboxId, eventId);
            acknowledgment.acknowledge();
            return;
        }

        Map<String, String> p = parse(payload);
        String publicOrderId = required(p, "publicOrderId", eventId);

        log.info("payment.transfer.consumer.received outboxId={} eventId={} publicOrderId={}",
                outboxId, eventId, publicOrderId);

        paymentService.transferToVendor(publicOrderId);
        processedKafkaEventService.markProcessed(consumerName, eventId, outboxId, eventType);
        acknowledgment.acknowledge();

        log.info("payment.transfer.consumer.acked outboxId={} eventId={} publicOrderId={}",
                outboxId, eventId, publicOrderId);
    }

    private Map<String, String> parse(String payload) throws Exception {
        return MAPPER.readValue(payload, new TypeReference<Map<String, String>>() {});
    }

    private String required(Map<String, String> payload, String key, String eventId) {
        String value = payload.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing event payload field '" + key + "' for payment transfer eventId=" + eventId);
        }
        return value;
    }
}
