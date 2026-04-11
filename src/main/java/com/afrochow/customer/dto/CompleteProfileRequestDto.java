package com.afrochow.customer.dto;

import com.afrochow.address.dto.AddressRequestDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Used by Google OAuth users to complete their profile after auto-creation.
 * Phone is required; address and username are optional (can be set at checkout).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompleteProfileRequestDto {

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^[+]?[0-9\\s\\-().]{7,20}$", message = "Invalid phone number format")
    private String phone;

    // Optional — auto-generated on registration if blank
    @Size(max = 50, message = "Username must be at most 50 characters")
    private String username;

    // Optional — can be collected at checkout instead
    @Valid
    private AddressRequestDto address;

    @Size(max = 500)
    private String defaultDeliveryInstructions;
}
