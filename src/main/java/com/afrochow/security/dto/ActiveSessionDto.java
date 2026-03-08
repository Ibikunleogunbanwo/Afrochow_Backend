package com.afrochow.security.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for active user sessions
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ActiveSessionDto {

    private Long tokenId;
    private String deviceInfo;
    private String ipAddress;
    private LocalDateTime lastUsedAt;
    private LocalDateTime createdAt;
    private Boolean isCurrentSession;
}
