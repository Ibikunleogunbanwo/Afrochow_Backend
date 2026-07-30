package com.afrochow.waitlist.service;

import com.afrochow.outbox.service.OutboxEventService;
import com.afrochow.waitlist.dto.WaitlistRequestDto;
import com.afrochow.waitlist.dto.WaitlistResponseDto;
import com.afrochow.waitlist.model.WaitlistEntry;
import com.afrochow.waitlist.repository.WaitlistEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WaitlistServiceTest {

    @Mock private WaitlistEntryRepository waitlistEntryRepository;
    @Mock private OutboxEventService outboxEventService;

    @InjectMocks private WaitlistService waitlistService;

    private WaitlistRequestDto request(String name, String email, String city, String role) {
        WaitlistRequestDto dto = new WaitlistRequestDto();
        dto.setName(name);
        dto.setEmail(email);
        dto.setCity(city);
        dto.setRole(role);
        return dto;
    }

    @Test
    void join_newEntry_savesAndFiresOutboxEvent() {
        when(waitlistEntryRepository.findByEmailAndRole("wendy@example.com", "CUSTOMER"))
                .thenReturn(Optional.empty());
        when(waitlistEntryRepository.save(any(WaitlistEntry.class))).thenAnswer(inv -> {
            WaitlistEntry e = inv.getArgument(0);
            e.setPublicWaitlistId("WAIT-ABCD1234");
            return e;
        });

        WaitlistResponseDto result = waitlistService.join(
                request("Wendy", "Wendy@Example.com", "Calgary", "customer"));

        assertThat(result.getEmail()).isEqualTo("wendy@example.com"); // normalized lowercase
        assertThat(result.getRole()).isEqualTo("CUSTOMER");
        assertThat(result.getCity()).isEqualTo("Calgary");
        verify(outboxEventService).waitlistJoined("WAIT-ABCD1234", "wendy@example.com", "Wendy", "CUSTOMER");
    }

    @Test
    void join_vendorRole_normalizesToUppercaseVendor() {
        when(waitlistEntryRepository.findByEmailAndRole("v@example.com", "VENDOR"))
                .thenReturn(Optional.empty());
        when(waitlistEntryRepository.save(any(WaitlistEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        WaitlistResponseDto result = waitlistService.join(
                request("Vendy", "v@example.com", null, "vendor"));

        assertThat(result.getRole()).isEqualTo("VENDOR");
    }

    @Test
    void join_unrecognizedRole_defaultsToCustomer() {
        when(waitlistEntryRepository.findByEmailAndRole("x@example.com", "CUSTOMER"))
                .thenReturn(Optional.empty());
        when(waitlistEntryRepository.save(any(WaitlistEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        WaitlistResponseDto result = waitlistService.join(
                request("Xavier", "x@example.com", null, "admin"));

        assertThat(result.getRole()).isEqualTo("CUSTOMER");
    }

    @Test
    void join_blankRole_defaultsToCustomer() {
        when(waitlistEntryRepository.findByEmailAndRole("blank@example.com", "CUSTOMER"))
                .thenReturn(Optional.empty());
        when(waitlistEntryRepository.save(any(WaitlistEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        WaitlistResponseDto result = waitlistService.join(
                request("Blank", "blank@example.com", null, "  "));

        assertThat(result.getRole()).isEqualTo("CUSTOMER");
    }

    @Test
    void join_blankCity_normalizesToNull() {
        when(waitlistEntryRepository.findByEmailAndRole("x@example.com", "CUSTOMER"))
                .thenReturn(Optional.empty());
        when(waitlistEntryRepository.save(any(WaitlistEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        WaitlistResponseDto result = waitlistService.join(
                request("Xavier", "x@example.com", "   ", null));

        assertThat(result.getCity()).isNull();
    }

    @Test
    void join_existingEmailAndRole_updatesInPlaceRatherThanCreatingDuplicate() {
        WaitlistEntry existing = WaitlistEntry.builder().id(1L).publicWaitlistId("WAIT-EXIST01")
                .email("wendy@example.com").role("CUSTOMER").name("Old Name").city("OldCity").build();
        when(waitlistEntryRepository.findByEmailAndRole("wendy@example.com", "CUSTOMER"))
                .thenReturn(Optional.of(existing));
        when(waitlistEntryRepository.save(any(WaitlistEntry.class))).thenAnswer(inv -> inv.getArgument(0));
        ArgumentCaptor<WaitlistEntry> captor = ArgumentCaptor.forClass(WaitlistEntry.class);

        WaitlistResponseDto result = waitlistService.join(
                request("New Name", "wendy@example.com", "NewCity", "customer"));

        verify(waitlistEntryRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(1L); // same row, not a new one
        assertThat(result.getName()).isEqualTo("New Name");
        assertThat(result.getCity()).isEqualTo("NewCity");
        assertThat(result.getPublicWaitlistId()).isEqualTo("WAIT-EXIST01");
    }

    @Test
    void join_nameIsTrimmed() {
        when(waitlistEntryRepository.findByEmailAndRole("x@example.com", "CUSTOMER"))
                .thenReturn(Optional.empty());
        when(waitlistEntryRepository.save(any(WaitlistEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        WaitlistResponseDto result = waitlistService.join(
                request("  Xavier  ", "x@example.com", null, null));

        assertThat(result.getName()).isEqualTo("Xavier");
    }
}
