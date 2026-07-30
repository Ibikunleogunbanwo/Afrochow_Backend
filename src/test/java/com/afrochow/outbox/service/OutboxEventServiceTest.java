package com.afrochow.outbox.service;

import com.afrochow.outbox.enums.OutboxEventType;
import com.afrochow.outbox.model.OutboxEvent;
import com.afrochow.outbox.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxEventServiceTest {

    @Mock private OutboxEventRepository outboxEventRepository;

    @InjectMocks private OutboxEventService outboxEventService;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(outboxEventService, "domainEventsTopic", "afrochow.domain-events");
    }

    private OutboxEvent captureSaved() {
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        return captor.getValue();
    }

    private Map<String, String> payloadOf(OutboxEvent event) throws Exception {
        return mapper.readValue(event.getPayload(), new TypeReference<Map<String, String>>() {});
    }

    // ========== order lifecycle ==========

    @Test
    void orderPlaced_savesEventWithCorrectTypeAndPayload() throws Exception {
        outboxEventService.orderPlaced("AFC-0001");

        OutboxEvent saved = captureSaved();
        assertThat(saved.getEventType()).isEqualTo(OutboxEventType.ORDER_PLACED);
        assertThat(saved.getAggregateType()).isEqualTo("ORDER");
        assertThat(saved.getAggregateId()).isEqualTo("AFC-0001");
        assertThat(saved.getTopic()).isEqualTo("afrochow.domain-events");
        assertThat(payloadOf(saved)).containsEntry("publicOrderId", "AFC-0001");
    }

    @Test
    void orderCancelled_nullReason_serializesAsEmptyString() throws Exception {
        outboxEventService.orderCancelled("AFC-0001", null, "PENDING", "SYSTEM");

        OutboxEvent saved = captureSaved();
        Map<String, String> payload = payloadOf(saved);
        assertThat(payload.get("reason")).isEmpty();
        assertThat(payload.get("previousStatus")).isEqualTo("PENDING");
        assertThat(payload.get("cancelledBy")).isEqualTo("SYSTEM");
    }

    @Test
    void orderCancelled_withReason_includesReasonInPayload() throws Exception {
        outboxEventService.orderCancelled("AFC-0001", "Vendor closed", "CONFIRMED", "VENDOR");

        Map<String, String> payload = payloadOf(captureSaved());
        assertThat(payload.get("reason")).isEqualTo("Vendor closed");
    }

    // ========== payment ==========

    @Test
    void paymentCaptured_formatsAmountAsPlainString() throws Exception {
        outboxEventService.paymentCaptured("CUS1", "PAY1", "AFC-0001", new BigDecimal("42.50"));

        OutboxEvent saved = captureSaved();
        assertThat(saved.getEventType()).isEqualTo(OutboxEventType.PAYMENT_CAPTURED);
        assertThat(saved.getAggregateType()).isEqualTo("PAYMENT");
        assertThat(saved.getAggregateId()).isEqualTo("PAY1");
        Map<String, String> payload = payloadOf(saved);
        assertThat(payload.get("amount")).isEqualTo("42.50"); // toPlainString() preserves scale
    }

    @Test
    void paymentFailed_nullReason_defaultsToUnknownError() throws Exception {
        outboxEventService.paymentFailed("CUS1", "AFC-0001", null);

        Map<String, String> payload = payloadOf(captureSaved());
        assertThat(payload.get("reason")).isEqualTo("Unknown error");
    }

    // ========== engagement ==========

    @Test
    void vendorReviewed_convertsRatingToString() throws Exception {
        outboxEventService.vendorReviewed("VEN1", "Ade", 5, "restaurant");

        OutboxEvent saved = captureSaved();
        assertThat(saved.getEventType()).isEqualTo(OutboxEventType.VENDOR_REVIEWED);
        assertThat(saved.getAggregateType()).isEqualTo("VENDOR");
        Map<String, String> payload = payloadOf(saved);
        assertThat(payload.get("rating")).isEqualTo("5");
    }

    @Test
    void vendorCertificateUploaded_nullFields_serializeAsEmptyString() throws Exception {
        outboxEventService.vendorCertificateUploaded("VEN1", "USR1", null, null);

        Map<String, String> payload = payloadOf(captureSaved());
        assertThat(payload.get("restaurantName")).isEmpty();
        assertThat(payload.get("certificateUrl")).isEmpty();
    }

    // ========== auth / account lifecycle ==========

    @Test
    void userRegistered_savesUserAggregateEvent() throws Exception {
        outboxEventService.userRegistered("CUS1", "c@example.com", "Ade", "CUSTOMER");

        OutboxEvent saved = captureSaved();
        assertThat(saved.getEventType()).isEqualTo(OutboxEventType.USER_REGISTERED);
        assertThat(saved.getAggregateType()).isEqualTo("USER");
        assertThat(saved.getAggregateId()).isEqualTo("CUS1");
    }

    // ========== address ==========

    @Test
    void addressGeocodingRequested_savesAddressAggregateEvent() throws Exception {
        outboxEventService.addressGeocodingRequested("ADDR1");

        OutboxEvent saved = captureSaved();
        assertThat(saved.getEventType()).isEqualTo(OutboxEventType.ADDRESS_GEOCODING_REQUESTED);
        assertThat(saved.getAggregateType()).isEqualTo("ADDRESS");
        assertThat(saved.getAggregateId()).isEqualTo("ADDR1");
    }

    // ========== broadcast ==========

    @Test
    void broadcastSent_generatesRandomAggregateIdAndCorrectPayload() throws Exception {
        outboxEventService.broadcastSent("Sale", "50% off", "PROMO", "ALL", "admin1");

        OutboxEvent saved = captureSaved();
        assertThat(saved.getEventType()).isEqualTo(OutboxEventType.BROADCAST_SENT);
        assertThat(saved.getAggregateType()).isEqualTo("BROADCAST");
        assertThat(saved.getAggregateId()).isNotBlank(); // generated UUID, not a domain ID
        Map<String, String> payload = payloadOf(saved);
        assertThat(payload).containsEntry("title", "Sale")
                .containsEntry("message", "50% off")
                .containsEntry("type", "PROMO")
                .containsEntry("targetAudience", "ALL")
                .containsEntry("sentBy", "admin1");
    }

    @Test
    void broadcastSent_calledTwice_generatesDifferentAggregateIds() throws Exception {
        outboxEventService.broadcastSent("Sale1", "msg", "PROMO", "ALL", "admin1");
        outboxEventService.broadcastSent("Sale2", "msg", "PROMO", "ALL", "admin1");

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getAggregateId())
                .isNotEqualTo(captor.getAllValues().get(1).getAggregateId());
    }

    // ========== waitlist ==========

    @Test
    void waitlistJoined_savesWaitlistAggregateEvent() throws Exception {
        outboxEventService.waitlistJoined("WL1", "w@example.com", "Wendy", "CUSTOMER");

        OutboxEvent saved = captureSaved();
        assertThat(saved.getEventType()).isEqualTo(OutboxEventType.WAITLIST_JOINED);
        assertThat(saved.getAggregateType()).isEqualTo("WAITLIST");
        assertThat(saved.getAggregateId()).isEqualTo("WL1");
    }

    // ========== topic wiring ==========

    @Test
    void allEvents_useConfiguredDomainEventsTopic() {
        ReflectionTestUtils.setField(outboxEventService, "domainEventsTopic", "custom.topic");

        outboxEventService.orderPlaced("AFC-0001");

        assertThat(captureSaved().getTopic()).isEqualTo("custom.topic");
    }
}
