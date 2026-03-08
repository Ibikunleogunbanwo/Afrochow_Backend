package com.afrochow.address.dto;
import com.afrochow.common.enums.Province;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;



@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressRequestDto {

    @NotBlank(message = "Address line is required")
    @Size(max = 200, message = "Address line must not exceed 200 characters")
    private String addressLine;

    @NotBlank(message = "City is required")
    @Size(max = 100, message = "City must not exceed 100 characters")
    private String city;

    @NotNull(message = "Province is required")
    private Province province;

    @NotBlank(message = "Postal code is required")
    @Pattern(
            regexp = "^[A-Za-z]\\d[A-Za-z][ -]?\\d[A-Za-z]\\d$",
            message = "Invalid Canadian postal code"
    )
    private String postalCode;

    @Builder.Default
    private String country = "Canada";

    @Builder.Default
    private Boolean defaultAddress = false;

    // Normalize postal code when setting
    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode == null ? null : postalCode.toUpperCase().replace(" ", "");
    }
}
