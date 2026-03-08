package com.afrochow.vendor.service;

import com.afrochow.address.dto.AddressRequestDto;
import com.afrochow.address.dto.AddressResponseDto;
import com.afrochow.address.model.Address;
import com.afrochow.address.repository.AddressRepository;
import com.afrochow.image.ImageUploadService;
import com.afrochow.user.model.User;
import com.afrochow.user.repository.UserRepository;
import com.afrochow.vendor.dto.VendorProfileResponseDto;
import com.afrochow.vendor.dto.VendorProfileUpdateRequestDto;
import com.afrochow.vendor.VendorMapper;
import com.afrochow.vendor.model.VendorProfile;
import com.afrochow.vendor.repository.VendorProfileRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

/**
 * Service for managing vendor profiles
 */
@Service
@RequiredArgsConstructor
public class VendorProfileService {

    private final VendorProfileRepository vendorProfileRepository;
    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final ImageUploadService imageUploadService;
    private final VendorMapper vendorMapper;

    /**
     * Get vendor profile
     */
    @Transactional(readOnly = true)
    public VendorProfileResponseDto getProfile(Long userId) {
        VendorProfile vendorProfile = getVendorProfileByUserId(userId);
        return vendorMapper.toResponseDto(vendorProfile);
    }

    /**
     * Get vendor profile by public vendor ID (public endpoint)
     */
    @Transactional(readOnly = true)
    public VendorProfileResponseDto getVendorByPublicId(String publicVendorId) {
        VendorProfile vendorProfile = vendorProfileRepository.findByPublicVendorId(publicVendorId)
                .orElseThrow(() -> new EntityNotFoundException("Vendor not found with ID: " + publicVendorId));
        return vendorMapper.toResponseDto(vendorProfile);
    }

    /**
     * Update vendor profile
     */
    @Transactional
    public VendorProfileResponseDto updateProfile(Long userId, VendorProfileUpdateRequestDto request) {
        VendorProfile vendorProfile = getVendorProfileByUserId(userId);

        // Validate business rules before updating
        validateUpdateRequest(request);

        // Update basic information
        updateIfNotNull(request.getRestaurantName(), vendorProfile::setRestaurantName);
        updateIfNotNull(request.getDescription(), vendorProfile::setDescription);
        updateIfNotNull(request.getCuisineType(), vendorProfile::setCuisineType);
        updateIfNotNull(request.getLogoUrl(), vendorProfile::setLogoUrl);
        updateIfNotNull(request.getBannerUrl(), vendorProfile::setBannerUrl);

        // Update business information
        updateIfNotNull(request.getBusinessLicenseUrl(), vendorProfile::setBusinessLicenseUrl);
        updateIfNotNull(request.getTaxId(), vendorProfile::setTaxId);

        // Update status (consider restricting isVerified to admin-only)
        updateIfNotNull(request.getIsActive(), vendorProfile::setIsActive);

        // Update operating hours
        if (request.getOperatingHours() != null) {
            Map<String, VendorProfile.DayHours> entityHours =
                    vendorMapper.convertToEntityOperatingHours(request.getOperatingHours());
            vendorProfile.setOperatingHours(entityHours);
        }

        // Update service options
        updateIfNotNull(request.getOffersDelivery(), vendorProfile::setOffersDelivery);
        updateIfNotNull(request.getOffersPickup(), vendorProfile::setOffersPickup);
        updateIfNotNull(request.getPreparationTime(), vendorProfile::setPreparationTime);

        // Update delivery settings
        updateIfNotNull(request.getDeliveryFee(), vendorProfile::setDeliveryFee);
        updateIfNotNull(request.getMinimumOrderAmount(), vendorProfile::setMinimumOrderAmount);
        updateIfNotNull(request.getEstimatedDeliveryMinutes(), vendorProfile::setEstimatedDeliveryMinutes);
        updateIfNotNull(request.getMaxDeliveryDistanceKm(), vendorProfile::setMaxDeliveryDistanceKm);

        vendorProfileRepository.save(vendorProfile);

        return vendorMapper.toResponseDto(vendorProfile);
    }

    /**
     * Update vendor address (vendors have exactly one address)
     */
    @Transactional
    public AddressResponseDto updateAddress(Long userId, AddressRequestDto request) {
        VendorProfile vendorProfile = getVendorProfileByUserId(userId);

        Address address = vendorProfile.getAddress();
        if (address == null) {
            throw new EntityNotFoundException("Vendor address not found");
        }

        // Update address fields
        updateIfNotNull(request.getAddressLine(), address::setAddressLine);
        updateIfNotNull(request.getCity(), address::setCity);
        updateIfNotNull(request.getProvince(), address::setProvince);
        updateIfNotNull(request.getPostalCode(), address::setPostalCode);
        updateIfNotNull(request.getCountry(), address::setCountry);

        address = addressRepository.save(address);

        return vendorMapper.toAddressResponseDto(address);
    }

    /**
     * Upload vendor logo
     */
    @Transactional
    public VendorProfileResponseDto uploadImage(String username, MultipartFile file, String type) throws IOException {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Image type must be provided");
        }

        type = type.trim().toLowerCase();

        User userEntity = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        VendorProfile vendorProfile = getVendorProfileByUserId(userEntity.getUserId());

        String path = switch (type) {
            case "logo" -> {
                deleteImageIfExists(vendorProfile.getLogoUrl());
                yield "vendors/logos";
            }
            case "banner" -> {
                deleteImageIfExists(vendorProfile.getBannerUrl());
                yield "vendors/banners";
            }
            default -> throw new IllegalArgumentException("Invalid image type: " + type);
        };

        String imageUrl = imageUploadService.uploadImageForRegistrationAndGetUrl(file, path);

        if (type.equals("logo")) {
            vendorProfile.setLogoUrl(imageUrl);
        } else {
            vendorProfile.setBannerUrl(imageUrl);
        }

        vendorProfile = vendorProfileRepository.save(vendorProfile);
        return vendorMapper.toResponseDto(vendorProfile);
    }





    // ========== HELPER METHODS ==========
    /**
     * Get vendor profile by user ID with validation
     */
    private VendorProfile getVendorProfileByUserId(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        if (!user.isVendor()) {
            throw new IllegalStateException("User is not a vendor");
        }

        VendorProfile vendorProfile = user.getVendorProfile();
        if (vendorProfile == null) {
            throw new EntityNotFoundException("Vendor profile not found");
        }

        return vendorProfile;
    }

    /**
     * Update field only if value is not null
     */
    private <T> void updateIfNotNull(T value, java.util.function.Consumer<T> setter) {
        if (value != null) {
            setter.accept(value);
        }
    }

    /**
     * Delete image if URL exists and is not empty
     */
    private void deleteImageIfExists(String imageUrl) {
        if (imageUrl != null && !imageUrl.isEmpty()) {
            imageUploadService.deleteImage(imageUrl);
        }
    }

    // ========== VALIDATION METHODS ==========

    private void validateUpdateRequest(VendorProfileUpdateRequestDto request) {
        if (request.getOffersDelivery() != null || request.getOffersPickup() != null) {
            if (!request.hasAtLeastOneService()) {
                throw new IllegalArgumentException(
                        "Vendor must offer at least delivery or pickup");
            }
        }
        if (request.getOperatingHours() != null && !request.hasAtLeastOneOpenDay()) {
            throw new IllegalArgumentException(
                    "Vendor must be open at least one day per week");
        }

        if (Boolean.TRUE.equals(request.getOffersDelivery())) {
            if (request.getDeliveryFee() == null) {
                throw new IllegalArgumentException(
                        "Delivery fee is required when offering delivery");
            }
        }
    }
}