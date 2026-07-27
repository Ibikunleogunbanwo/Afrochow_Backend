package com.afrochow.waitlist.service;

import com.afrochow.waitlist.dto.WaitlistRequestDto;
import com.afrochow.waitlist.dto.WaitlistResponseDto;
import com.afrochow.waitlist.model.WaitlistEntry;
import com.afrochow.waitlist.repository.WaitlistEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class WaitlistService {

    private final WaitlistEntryRepository waitlistEntryRepository;

    public WaitlistService(WaitlistEntryRepository waitlistEntryRepository) {
        this.waitlistEntryRepository = waitlistEntryRepository;
    }

    @Transactional
    public WaitlistResponseDto join(WaitlistRequestDto request) {
        String email = normalizeEmail(request.getEmail());
        String role = normalizeRole(request.getRole());

        WaitlistEntry entry = waitlistEntryRepository.findByEmailAndRole(email, role)
                .orElseGet(() -> WaitlistEntry.builder()
                        .email(email)
                        .role(role)
                        .build());

        entry.setName(request.getName().trim());
        entry.setCity(normalizeOptional(request.getCity()));

        return toDto(waitlistEntryRepository.save(entry));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeRole(String role) {
        String normalized = normalizeOptional(role);
        if (normalized == null) {
            return "CUSTOMER";
        }

        String upper = normalized.toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "VENDOR" -> "VENDOR";
            default -> "CUSTOMER";
        };
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private WaitlistResponseDto toDto(WaitlistEntry entry) {
        return WaitlistResponseDto.builder()
                .publicWaitlistId(entry.getPublicWaitlistId())
                .name(entry.getName())
                .email(entry.getEmail())
                .city(entry.getCity())
                .role(entry.getRole())
                .createdAt(entry.getCreatedAt())
                .updatedAt(entry.getUpdatedAt())
                .build();
    }
}
