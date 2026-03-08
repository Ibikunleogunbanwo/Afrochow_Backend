package com.afrochow.vendor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendorVerificationResponseDto {

    private String publicVendorId;
    private String restaurantName;
    private Boolean isVerified;
    private LocalDateTime verifiedAt;
    private String verifiedBy; // Admin who verified
    private String message;
}