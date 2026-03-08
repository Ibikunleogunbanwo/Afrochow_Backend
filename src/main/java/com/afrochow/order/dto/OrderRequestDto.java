package com.afrochow.order.dto;

import com.afrochow.orderline.dto.OrderLineRequestDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequestDto {

    @NotNull(message = "Vendor ID is required")
    private String vendorPublicId;

    @NotNull(message = "Delivery address ID is required")
    private String deliveryAddressPublicId;



    @NotEmpty(message = "Order must contain at least one item")
    @Valid
    private List<OrderLineRequestDto> orderLines;

    @Size(max = 500, message = "Special instructions must not exceed 500 characters")
    private String specialInstructions;
}