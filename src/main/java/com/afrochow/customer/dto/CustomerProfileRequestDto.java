package com.afrochow.customer.dto;

import com.afrochow.address.dto.AddressRequestDto;
import com.afrochow.auth.dto.BaseRegistrationRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerProfileRequestDto extends BaseRegistrationRequest {


    // ========== REQUIRED USERNAME (FOR BASE CLASS) ==========
    @Schema(description = "Username (optional - auto-generated if not provided)")
    private String username;

    @Override
    public String getUsername() {
        return this.username;
    }

    // Customer-specific fields
    @Size(max = 500)
    private String defaultDeliveryInstructions;

    @Valid
    @NotNull(message = "Address is required")
    private AddressRequestDto address;

    // Cross-field validation
    @AssertTrue(message = "Passwords do not match")
    public boolean isPasswordMatching() {
        return getPassword() != null && getPassword().equals(getConfirmPassword());
    }
}
