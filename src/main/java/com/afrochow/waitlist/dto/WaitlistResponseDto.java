package com.afrochow.waitlist.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class WaitlistResponseDto {
    String publicWaitlistId;
    String name;
    String email;
    String city;
    String role;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
