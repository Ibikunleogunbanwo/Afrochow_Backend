package com.afrochow.address.controller;

import com.afrochow.address.dto.AddressRequestDto;
import com.afrochow.address.dto.AddressResponseDto;
import com.afrochow.address.service.AddressService;
import com.afrochow.common.ApiResponse;
import com.afrochow.security.model.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/customer/addresses")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Customer Addresses", description = "Customer address management endpoints")
public class CustomerAddressController {

    private final AddressService addressService;

    @GetMapping
    @Operation(summary = "Get all addresses", description = "Get all addresses for the authenticated customer")
    public ApiResponse<List<AddressResponseDto>> getAllAddresses(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<AddressResponseDto> addresses = addressService.getCustomerAddresses(userDetails.getPublicUserId());
        return ApiResponse.success("Addresses retrieved successfully", addresses);
    }

    @GetMapping("/{publicAddressId}")
    @Operation(summary = "Get address", description = "Get a specific address by public ID")
    public ApiResponse<AddressResponseDto> getAddress(
            @PathVariable String publicAddressId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        AddressResponseDto address = addressService.getAddress(userDetails.getPublicUserId(), publicAddressId);
        return ApiResponse.success("Address retrieved successfully", address);
    }

    @PostMapping
    @Operation(summary = "Add address", description = "Add a new delivery address")
    public ApiResponse<AddressResponseDto> addAddress(
            @Valid @RequestBody AddressRequestDto request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        AddressResponseDto address = addressService.addAddress(userDetails.getPublicUserId(), request);
        return ApiResponse.success("Address added successfully", address);
    }

    @PutMapping("/{publicAddressId}")
    @Operation(summary = "Update address", description = "Update an existing address")
    public ApiResponse<AddressResponseDto> updateAddress(
            @PathVariable String publicAddressId,
            @Valid @RequestBody AddressRequestDto request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        AddressResponseDto address = addressService.updateAddress(userDetails.getPublicUserId(), publicAddressId, request);
        return ApiResponse.success("Address updated successfully", address);
    }

    @DeleteMapping("/{publicAddressId}")
    @Operation(summary = "Delete address", description = "Delete an address")
    public ApiResponse<Void> deleteAddress(
            @PathVariable String publicAddressId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        addressService.deleteAddress(userDetails.getPublicUserId(), publicAddressId);
        return ApiResponse.success("Address deleted successfully");
    }

    @PostMapping("/{publicAddressId}/set-default")
    @Operation(summary = "Set default address", description = "Set an address as the default delivery address")
    public ApiResponse<AddressResponseDto> setDefaultAddress(
            @PathVariable String publicAddressId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        AddressResponseDto address = addressService.setDefaultAddress(userDetails.getPublicUserId(), publicAddressId);
        return ApiResponse.success("Address set as default", address);
    }
}
