package com.afrochow.address.controller;

import com.afrochow.address.dto.AddressRequestDto;
import com.afrochow.address.dto.AddressResponseDto;
import com.afrochow.common.ApiResponse;
import com.afrochow.security.model.CustomUserDetails;
import com.afrochow.address.service.AddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for customer address management
 */
@RestController
@RequestMapping("/customer/addresses")
@Tag(name = "Customer Addresses", description = "Customer address management endpoints")
public class CustomerAddressController {

    private final AddressService addressService;

    public CustomerAddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    /**
     * Get all addresses for the authenticated customer
     */
    @GetMapping
    @Operation(summary = "Get all addresses", description = "Get all addresses for the authenticated customer")
    public ResponseEntity<ApiResponse<List<AddressResponseDto>>> getAllAddresses(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        List<AddressResponseDto> addresses = addressService.getCustomerAddresses(userDetails.getPublicUserId());
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Addresses retrieved successfully",
                        addresses )
        );
    }

    /**
     * Get a specific address by ID
     */
    @GetMapping("/{publicAddressId}")
    @Operation(summary = "Get address", description = "Get a specific address by public ID")
    public ResponseEntity<ApiResponse<AddressResponseDto>> getAddress(
            @PathVariable String publicAddressId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        AddressResponseDto address = addressService.getAddress(userDetails.getPublicUserId(), publicAddressId);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Address retrieved successfully",
                        address )
        );
    }

    /**
     * Add a new address
     */
    @PostMapping
    @Operation(summary = "Add address", description = "Add a new delivery address")
    public ResponseEntity<ApiResponse<AddressResponseDto>> addAddress(
            @Valid @RequestBody AddressRequestDto request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        AddressResponseDto address = addressService.addAddress(userDetails.getPublicUserId(), request);
        return ResponseEntity.ok(
            ApiResponse.success(
                "Address added successfully",
                address )
             );
    }

    /**
     * Update an existing address
     */
    @PutMapping("/{publicAddressId}")
    @Operation(summary = "Update address", description = "Update an existing address")
    public ResponseEntity<ApiResponse<AddressResponseDto>> updateAddress(
            @PathVariable String publicAddressId,
            @Valid @RequestBody AddressRequestDto request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        AddressResponseDto address = addressService.updateAddress(userDetails.getPublicUserId(), publicAddressId, request);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Address updated successfully",
                        address )
        );
    }

    /**
     * Delete an address
     */
    @DeleteMapping("/{publicAddressId}")
    @Operation(summary = "Delete address", description = "Delete an address")
    public ResponseEntity<ApiResponse<String>>deleteAddress(
            @PathVariable String publicAddressId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        addressService.deleteAddress(userDetails.getPublicUserId(), publicAddressId);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Address deleted successfully"
                         )
        );
    }

    /**
     * Set an address as the default address
     */
    @PostMapping("/{publicAddressId}/set-default")
    @Operation(summary = "Set default address", description = "Set an address as the default delivery address")
    public ResponseEntity<ApiResponse<AddressResponseDto>> setDefaultAddress(
            @PathVariable String publicAddressId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        AddressResponseDto address = addressService.setDefaultAddress(userDetails.getPublicUserId(), publicAddressId);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Address Set as  Default",
                        address
                )
        );
    }
}
