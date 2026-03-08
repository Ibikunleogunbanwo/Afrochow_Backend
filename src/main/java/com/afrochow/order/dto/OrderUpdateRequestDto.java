package com.afrochow.order.dto;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderUpdateRequestDto {

    @Size(max = 500, message = "Special instructions must not exceed 500 characters")
    private String specialInstructions;

    private String deliveryAddressPublicId;
}