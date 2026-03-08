package com.afrochow.customer.dto;
import com.afrochow.address.dto.AddressResponseDto;
import com.afrochow.common.enums.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerProfileResponseDto {
    private String publicUserId;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String profileImageUrl;
    private Integer loyaltyPoints;
    private String defaultDeliveryInstructions;
    private Integer totalOrders;
    private List<AddressResponseDto> addresses;
    private LocalDateTime createdAt;
}
