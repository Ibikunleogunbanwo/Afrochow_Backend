package com.afrochow.payment.dto;
import com.afrochow.common.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequestDto {

    @NotNull(message = "Order ID is required")
    private String orderPublicId;

    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;

    @Size(max = 100, message = "Transaction ID must not exceed 100 characters")
    private String transactionId;

    @Size(max = 500, message = "Notes must not exceed 500 characters")
    private String notes;
}