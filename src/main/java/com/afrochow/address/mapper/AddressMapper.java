package com.afrochow.address.mapper;

import com.afrochow.address.dto.AddressResponseDto;
import com.afrochow.address.model.Address;
import org.springframework.stereotype.Component;

/**
 * Converts address entities into API response DTOs.
 */
@Component
public class AddressMapper {

    public AddressResponseDto toResponseDto(Address address) {
        if (address == null) {
            return null;
        }

        return AddressResponseDto.builder()
                .publicAddressId(address.getPublicAddressId())
                .addressLine(address.getAddressLine())
                .city(address.getCity())
                .province(address.getProvince())
                .postalCode(address.getPostalCode())
                .country(address.getCountry())
                .defaultAddress(address.getDefaultAddress())
                .formattedAddress(address.getFormattedAddress())
                .createdAt(address.getCreatedAt())
                .updatedAt(address.getUpdatedAt())
                .build();
    }
}
