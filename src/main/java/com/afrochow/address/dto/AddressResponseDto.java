package com.afrochow.address.dto;

import com.afrochow.common.enums.Province;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AddressResponseDto {

    private String publicAddressId;

    private String addressLine;
    private String city;
    private Province province;
    private String postalCode;
    private String country;

    private Boolean defaultAddress;

    private String formattedAddress;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
