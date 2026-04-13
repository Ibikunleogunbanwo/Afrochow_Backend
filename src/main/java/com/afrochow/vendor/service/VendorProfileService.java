package com.afrochow.vendor.service;

import com.afrochow.address.dto.AddressRequestDto;
import com.afrochow.address.dto.AddressResponseDto;
import com.afrochow.address.model.Address;
import com.afrochow.address.repository.AddressRepository;
import com.afrochow.common.enums.VendorStatus;
import com.afrochow.image.ImageUploadService;
import com.afrochow.user.model.User;
import com.afrochow.user.repository.UserRepository;
import com.afrochow.vendor.dto.FoodHandlingCertUploadRequestDto;
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
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Service for managing vendor profiles.
 *
 * Status transitions owned by this service (vendor-initiated):
 *   PENDING_PROFILE  → PENDING_REVIEW   (via updateProfile when profile is sufficiently complete)
 *   REJECTED         → PENDING_REVIEW   (via resubmitForReview)
 *   PROVISIONAL      → PROVISIONAL      (cert upload — triggers admin review of cert)
 *
 * Status transitions owned by AdminVendorManagementService (admin-initiated):
 *   PENDING_REVIEW   → PROVISIONAL / REJECTED
 *   PROVISIONAL      → VERIFIED / REJECTED
 *   VERIFIED         → SUSPENDED / REJECTED
 *   SUSPENDED        → VERIFIED
 */
@Service
@RequiredArgsConstructor
public class VendorProfileService {

    private final VendorProfileRepository vendorProfileRepository;
    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final ImageUploadService imageUploadService;
    private final VendorMapper vendorMapper;

    /** Statuses from which a vendor can still edit their own profile. */
    private static final Set<VendorStatus> EDITABLE_STATUSES = EnumSet.of(
            VendorStatus.PENDING_PROFILE,
            VendorStatus.PENDING_REVIEW,
            VendorStatus.PROVISIONAL,
            VendorStatus.REJECTED
    );

    // ========== READ ==========

    @Transactional(readOnly = true)
    public VendorProfileResponseDto getProfile(Long userId) {
        VendorProfile vendorProfile = getVendorProfileByUserId(userId);
        return vendorMapper.toResponseDto(vendorProfile);
    }

    // ========== PROFILE UPDATE ==========

    /**
     * Update vendor profile. If the vendor is in PENDING_PROFILE and the updated
     * profile is now sufficiently complete, auto-advance to PENDING_REVIEW.
     */
    @Transactional
    public VendorProfileResponseDto updateProfile(Long userId, VendorProfileUpdateRequestDto request) {
        VendorProfile vendorProfile = getVendorProfileByUserId(userId);

        if (!EDITABLE_STATUSES.contains(vendorProfile.getVendorStatus())) {
            throw new IllegalStateException(
                    "Profile cannot be edited in status: " + vendorProfile.getVendorStatus());
        }

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

        // Auto-advance: PENDING_PROFILE → PENDING_REVIEW when profile is complete
        if (vendorProfile.getVendorStatus() == VendorStatus.PENDING_PROFILE
                && isProfileComplete(vendorProfile)) {
            vendorProfile.setVendorStatus(VendorStatus.PENDING_REVIEW);
            // Keep deprecated booleans in sync
            vendorProfile.setIsActive(true);
            vendorProfile.setIsVerified(false);
        }

        vendorProfileRepository.save(vendorProfile);
        return vendorMapper.toResponseDto(vendorProfile);
    }

    // ========== ADDRESS UPDATE ==========

    @Transactional
    public AddressResponseDto updateAddress(Long userId, AddressRequestDto request) {
        VendorProfile vendorProfile = getVendorProfileByUserId(userId);

        Address address = vendorProfile.getAddress();
        if (address == null) {
            throw new EntityNotFoundException("Vendor address not found");
        }

        updateIfNotNull(request.getAddressLine(), address::setAddressLine);
        updateIfNotNull(request.getCity(), address::setCity);
        updateIfNotNull(request.getProvince(), address::setProvince);
        updateIfNotNull(request.getPostalCode(), address::setPostalCode);
        updateIfNotNull(request.getCountry(), address::setCountry);

        address = addressRepository.save(address);
        return vendorMapper.toAddressResponseDto(address);
    }

    // ========== IMAGE UPLOAD ==========

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

    // ========== FOOD HANDLING CERTIFICATE UPLOAD ==========

    /**
     * Vendor uploads their food handling certificate.
     * Only allowed when status is PROVISIONAL (cert is the missing step before VERIFIED).
     * Saves the cert metadata and notifies admins via the existing outbox pattern
     * (the admin controller handles the actual cert verification step).
     */
    @Transactional
    public VendorProfileResponseDto uploadFoodHandlingCert(Long userId,
                                                            MultipartFile certFile,
                                                            FoodHandlingCertUploadRequestDto metadata)
            throws IOException {

        VendorProfile vendorProfile = getVendorProfileByUserId(userId);

        if (vendorProfile.getVendorStatus() != VendorStatus.PROVISIONAL) {
            throw new IllegalStateException(
                    "Food handling certificate can only be uploaded when your store is in PROVISIONAL status. " +
                    "Current status: " + vendorProfile.getVendorStatus());
        }

        // Delete old cert if one exists
        deleteImageIfExists(vendorProfile.getFoodHandlingCertUrl());

        String certUrl = imageUploadService.uploadImageForRegistrationAndGetUrl(
                certFile, "vendors/certifications");

        vendorProfile.setFoodHandlingCertUrl(certUrl);
        vendorProfile.setFoodHandlingCertNumber(metadata.getCertNumber());
        vendorProfile.setFoodHandlingCertIssuingBody(metadata.getIssuingBody());
        vendorProfile.setFoodHandlingCertExpiry(metadata.getCertExpiry());

        // Clear any previous cert verification since a new doc was uploaded
        vendorProfile.setCertVerifiedAt(null);
        vendorProfile.setCertVerifiedByAdminId(null);

        vendorProfileRepository.save(vendorProfile);
        return vendorMapper.toResponseDto(vendorProfile);
    }

    // ========== RESUBMIT AFTER REJECTION ==========

    /**
     * Vendor resubmits their application after being rejected.
     * Moves status back to PENDING_REVIEW so it appears in the admin queue.
     */
    @Transactional
    public VendorProfileResponseDto resubmitForReview(Long userId) {
        VendorProfile vendorProfile = getVendorProfileByUserId(userId);

        if (vendorProfile.getVendorStatus() != VendorStatus.REJECTED) {
            throw new IllegalStateException(
                    "Only rejected vendors can resubmit. Current status: " + vendorProfile.getVendorStatus());
        }

        vendorProfile.setVendorStatus(VendorStatus.PENDING_REVIEW);
        // Keep deprecated booleans in sync
        vendorProfile.setIsActive(true);
        vendorProfile.setIsVerified(false);

        vendorProfileRepository.save(vendorProfile);
        return vendorMapper.toResponseDto(vendorProfile);
    }

    // ========== PRIVATE HELPERS ==========

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
     * A profile is considered complete enough to enter the review queue when it has
     * a restaurant name, cuisine type, logo, at least one service option, and an address.
     */
    private boolean isProfileComplete(VendorProfile profile) {
        return profile.getRestaurantName() != null && !profile.getRestaurantName().isBlank()
                && profile.getCuisineType() != null && !profile.getCuisineType().isBlank()
                && profile.getLogoUrl() != null && !profile.getLogoUrl().isBlank()
                && (Boolean.TRUE.equals(profile.getOffersDelivery())
                        || Boolean.TRUE.equals(profile.getOffersPickup()))
                && profile.getAddress() != null
                && profile.hasOperatingDays();
    }

    private <T> void updateIfNotNull(T value, java.util.function.Consumer<T> setter) {
        if (value != null) {
            setter.accept(value);
        }
    }

    private void deleteImageIfExists(String imageUrl) {
        if (imageUrl != null && !imageUrl.isEmpty()) {
            imageUploadService.deleteImage(imageUrl);
        }
    }

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
