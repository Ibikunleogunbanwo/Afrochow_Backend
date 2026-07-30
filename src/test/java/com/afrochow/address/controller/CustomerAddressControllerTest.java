package com.afrochow.address.controller;

import com.afrochow.address.dto.AddressRequestDto;
import com.afrochow.address.dto.AddressResponseDto;
import com.afrochow.address.service.AddressService;
import com.afrochow.common.enums.Province;
import com.afrochow.testsupport.AbstractControllerTest;
import com.afrochow.testsupport.ControllerSliceTest;
import com.afrochow.user.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-layer test for CustomerAddressController.
 *
 * Every endpoint here takes {@code @AuthenticationPrincipal CustomUserDetails},
 * so this is entirely exercised via {@code authenticatedAsPrincipal} — see
 * AbstractControllerTest / ControllerSliceTest for why that needs the
 * explicitly-imported {@code ArgumentResolverConfig}.
 */
@ControllerSliceTest(CustomerAddressController.class)
class CustomerAddressControllerTest extends AbstractControllerTest {

    @MockitoBean private AddressService addressService;

    private static final String CUSTOMER_PUBLIC_ID = "customer-1";

    private User customerUser() {
        return User.builder().publicUserId(CUSTOMER_PUBLIC_ID).build();
    }

    private AddressResponseDto sampleAddress(String publicAddressId) {
        return AddressResponseDto.builder()
                .publicAddressId(publicAddressId)
                .addressLine("123 Main St")
                .city("Calgary")
                .province(Province.AB)
                .postalCode("T2N1N4")
                .country("Canada")
                .defaultAddress(true)
                .formattedAddress("123 Main St, Calgary, AB, T2N1N4, Canada")
                .build();
    }

    private AddressRequestDto validRequest() {
        return AddressRequestDto.builder()
                .addressLine("123 Main St")
                .city("Calgary")
                .province(Province.AB)
                .postalCode("T2N1N4")
                .country("Canada")
                .defaultAddress(false)
                .build();
    }

    @Test
    void getAllAddresses_returns200() throws Exception {
        when(addressService.getCustomerAddresses(CUSTOMER_PUBLIC_ID))
                .thenReturn(List.of(sampleAddress("addr-1")));

        mockMvc.perform(get("/customer/addresses")
                        .with(authenticatedAsPrincipal(customerUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].publicAddressId").value("addr-1"));
    }

    @Test
    void getAddress_returns200() throws Exception {
        when(addressService.getAddress(CUSTOMER_PUBLIC_ID, "addr-1"))
                .thenReturn(sampleAddress("addr-1"));

        mockMvc.perform(get("/customer/addresses/{publicAddressId}", "addr-1")
                        .with(authenticatedAsPrincipal(customerUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.city").value("Calgary"));
    }

    @Test
    void getAddress_notFound_returns404() throws Exception {
        when(addressService.getAddress(CUSTOMER_PUBLIC_ID, "ghost"))
                .thenThrow(new jakarta.persistence.EntityNotFoundException("Address not found"));

        mockMvc.perform(get("/customer/addresses/{publicAddressId}", "ghost")
                        .with(authenticatedAsPrincipal(customerUser())))
                .andExpect(status().isNotFound());
    }

    @Test
    void addAddress_valid_returns200() throws Exception {
        when(addressService.addAddress(eq(CUSTOMER_PUBLIC_ID), any(AddressRequestDto.class)))
                .thenReturn(sampleAddress("addr-1"));

        mockMvc.perform(post("/customer/addresses")
                        .with(authenticatedAsPrincipal(customerUser()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publicAddressId").value("addr-1"));
    }

    @Test
    void addAddress_missingRequiredFields_returns400WithValidationErrors() throws Exception {
        AddressRequestDto request = validRequest();
        request.setAddressLine(null);
        request.setCity(null);

        mockMvc.perform(post("/customer/addresses")
                        .with(authenticatedAsPrincipal(customerUser()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(addressService, never()).addAddress(any(), any());
    }

    @Test
    void addAddress_invalidPostalCode_returns400() throws Exception {
        AddressRequestDto request = validRequest();
        request.setPostalCode("NOTVALID");

        mockMvc.perform(post("/customer/addresses")
                        .with(authenticatedAsPrincipal(customerUser()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateAddress_valid_returns200() throws Exception {
        when(addressService.updateAddress(eq(CUSTOMER_PUBLIC_ID), eq("addr-1"), any(AddressRequestDto.class)))
                .thenReturn(sampleAddress("addr-1"));

        mockMvc.perform(put("/customer/addresses/{publicAddressId}", "addr-1")
                        .with(authenticatedAsPrincipal(customerUser()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publicAddressId").value("addr-1"));
    }

    @Test
    void updateAddress_notOwnedByUser_returns400() throws Exception {
        when(addressService.updateAddress(eq(CUSTOMER_PUBLIC_ID), eq("addr-1"), any(AddressRequestDto.class)))
                .thenThrow(new IllegalStateException("Address does not belong to this customer"));

        mockMvc.perform(put("/customer/addresses/{publicAddressId}", "addr-1")
                        .with(authenticatedAsPrincipal(customerUser()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteAddress_returns200() throws Exception {
        doNothing().when(addressService).deleteAddress(CUSTOMER_PUBLIC_ID, "addr-1");

        mockMvc.perform(delete("/customer/addresses/{publicAddressId}", "addr-1")
                        .with(authenticatedAsPrincipal(customerUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void setDefaultAddress_returns200() throws Exception {
        when(addressService.setDefaultAddress(CUSTOMER_PUBLIC_ID, "addr-1"))
                .thenReturn(sampleAddress("addr-1"));

        mockMvc.perform(post("/customer/addresses/{publicAddressId}/set-default", "addr-1")
                        .with(authenticatedAsPrincipal(customerUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaultAddress").value(true));
    }
}
