package com.afrochow.vendor.service;

import com.afrochow.address.dto.AddressRequestDto;
import com.afrochow.address.dto.AddressResponseDto;
import com.afrochow.address.model.Address;
import com.afrochow.address.repository.AddressRepository;
import com.afrochow.common.enums.VendorStatus;
import com.afrochow.image.ImageUploadService;
import com.afrochow.image.service.ImageCleanupService;
import com.afrochow.outbox.service.OutboxEventService;
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
    private final ImageCleanupService imageCleanupService;
    private final OutboxEventService outboxEventService;
    private final VendorMapper vendorMapper;

    /**
     * Statuses from which a vendor can edit every field, including identity/compliance
     * ones (restaurant name, category, tax ID, business license) — i.e. anything short
     * of being fully verified or suspended.
     */
    private static final Set<VendorStatus> FULLY_EDITABLE_STATUSES = EnumSet.of(
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
     *
     * <p>Once a vendor is VERIFIED, identity/compliance fields (restaurant name, store
     * category, business license, tax ID) are locked — changing your legal business
     * identity after approval should go through support, not a self-serve save. But
     * day-to-day operational fields (hours, delivery settings, description, logo,
     * banner) stay editable indefinitely; a live vendor still needs to update their
     * hours for a holiday or change their delivery fee without losing the ability to
     * manage their own store. SUSPENDED vendors can't edit anything.
     */
    @Transactional
    public VendorProfileResponseDto updateProfile(Long userId, VendorProfileUpdateRequestDto request) {
        VendorProfile vendorProfile = getVendorProfileByUserId(userId);
        VendorStatus status = vendorProfile.getVendorStatus();

        boolean fullyEditable   = FULLY_EDITABLE_STATUSES.contains(status);
        boolean operationalOnly = status == VendorStatus.VERIFIED;

        if (!fullyEditable && !operationalOnly) {
            throw new IllegalStateException(
                    "Profile cannot be edited in status: " + status);
        }

        validateUpdateRequest(request);

        // Identity / compliance fields — locked once verified, editable before that.
        if (fullyEditable) {
            updateIfNotNull(request.getRestaurantName(), vendorProfile::setRestaurantName);
            updateIfNotNull(request.getStoreCategory(), vendorProfile::setStoreCategory);
            updateIfNotNull(request.getBusinessLicenseUrl(), vendorProfile::setBusinessLicenseUrl);
            updateIfNotNull(request.getTaxId(), vendorProfile::setTaxId);
        }

        // Operational fields — always editable while the vendor can edit at all
        // (both fullyEditable and operationalOnly/VERIFIED).
        updateIfNotNull(request.getDescription(), vendorProfile::setDescription);
        updateIfNotNull(request.getLogoUrl(), vendorProfile::setLogoUrl);
        updateIfNotNull(request.getBannerUrl(), vendorProfile::setBannerUrl);

        if (request.getOperatingHours() != null) {
            Map<String, VendorProfile.DayHours> entityHours =
                    vendorMapper.convertToEntityOperatingHours(request.getOperatingHours());
            vendorProfile.setOperatingHours(entityHours);
        }

        updateIfNotNull(request.getOffersDelivery(), vendorProfile::setOffersDelivery);
        updateIfNotNull(request.getOffersPickup(), vendorProfile::setOffersPickup);
        updateIfNotNull(request.getPreparationTime(), vendorProfile::setPreparationTime);

        updateIfNotNull(request.getDeliveryFee(), vendorProfile::setDeliveryFee);
        updateIfNotNull(request.getMinimumOrderAmount(), vendorProfile::setMinimumOrderAmount);
        updateIfNotNull(request.getEstimatedDeliveryMinutes(), vendorProfile::setEstimatedDeliveryMinutes);
        updateIfNotNull(request.getMaxDeliveryDistanceKm(), vendorProfile::setMaxDeliveryDistanceKm);

        // Auto-advance only after email verification. Admin review should never
        // contain vendors whose owner email is still unverified.
        if (status == VendorStatus.PENDING_PROFILE && isProfileComplete(vendorProfile)) {
            if (Boolean.TRUE.equals(vendorProfile.getUser().getEmailVerified())) {
                vendorProfile.setVendorStatus(VendorStatus.PENDING_REVIEW);
                // Keep deprecated booleans in sync
                vendorProfile.setIsActive(true);
                vendorProfile.setIsVerified(false);
            } else {
                vendorProfile.setIsActive(false);
                vendorProfile.setIsVerified(false);
            }
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

        boolean addressLineChanged = request.getAddressLine() != null
                && !request.getAddressLine().equals(address.getAddressLine());
        boolean cityChanged = request.getCity() != null
                && !request.getCity().equals(address.getCity());
        boolean postalCodeChanged = request.getPostalCode() != null
                && !request.getPostalCode().equals(address.getPostalCode());
        boolean provinceChanged = request.getProvince() != null
                && !request.getProvince().equals(address.getProvince());

        updateIfNotNull(request.getAddressLine(), address::setAddressLine);
        updateIfNotNull(request.getCity(), address::setCity);
        updateIfNotNull(request.getProvince(), address::setProvince);
        updateIfNotNull(request.getPostalCode(), address::setPostalCode);
        updateIfNotNull(request.getCountry(), address::setCountry);

        address = addressRepository.save(address);
        if (addressLineChanged || cityChanged || postalCodeChanged || provinceChanged) {
            outboxEventService.addressGeocodingRequested(address.getPublicAddressId());
        }
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

        String oldImageUrl = switch (type) {
            case "logo" -> vendorProfile.getLogoUrl();
            case "banner" -> vendorProfile.getBannerUrl();
            default -> throw new IllegalArgumentException("Invalid image type: " + type);
        };
        String path = type.equals("logo") ? "vendors/logos" : "vendors/banners";

        String imageUrl = imageUploadService.uploadImageForRegistrationAndGetUrl(file, path);

        if (type.equals("logo")) {
            vendorProfile.setLogoUrl(imageUrl);
        } else {
            vendorProfile.setBannerUrl(imageUrl);
        }

        vendorProfile = vendorProfileRepository.save(vendorProfile);
        enqueueImageCleanup(oldImageUrl, "vendor-" + type + "-replaced");
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

        String oldCertUrl = vendorProfile.getFoodHandlingCertUrl();

        String certUrl = imageUploadService.uploadCertificateForRegistrationAndGetUrl(
                certFile, "vendors/certifications");

        vendorProfile.setFoodHandlingCertUrl(certUrl);
        vendorProfile.setFoodHandlingCertNumber(metadata.getCertNumber());
        vendorProfile.setFoodHandlingCertIssuingBody(metadata.getIssuingBody());
        vendorProfile.setFoodHandlingCertExpiry(metadata.getCertExpiry());

        // Clear any previous cert verification since a new doc was uploaded
        vendorProfile.setCertVerifiedAt(null);
        vendorProfile.setCertVerifiedByAdminId(null);

        vendorProfileRepository.save(vendorProfile);
        outboxEventService.vendorCertificateUploaded(
                vendorProfile.getPublicVendorId(),
                vendorProfile.getUser().getPublicUserId(),
                vendorProfile.getRestaurantName(),
                certUrl);
        enqueueImageCleanup(oldCertUrl, "vendor-certification-replaced");
        return vendorMapper.toResponseDto(vendorProfile);
    }

    // ========== RESUBMIT AFTER REJECTION ==========

    /**
     * Submits a vendor's application for admin review.
     *
     * Allowed from:
     * <ul>
     *   <li>{@code PENDING_PROFILE} – first-time submission. Requires the profile
     *       to be complete (same check as the auto-advance in updateProfile).</li>
     *   <li>{@code REJECTED} – resubmission after a rejection decision.</li>
     * </ul>
     */
    @Transactional
    public VendorProfileResponseDto resubmitForReview(Long userId) {
        VendorProfile vendorProfile = getVendorProfileByUserId(userId);

        VendorStatus current = vendorProfile.getVendorStatus();

        if (current != VendorStatus.REJECTED && current != VendorStatus.PENDING_PROFILE) {
            throw new IllegalStateException(
                    "Cannot submit for review from current status: " + current);
        }

        // First-time submission: enforce profile completeness before advancing.
        if (current == VendorStatus.PENDING_PROFILE && !isProfileComplete(vendorProfile)) {
            throw new IllegalStateException(
                    "Profile is incomplete. Please fill in your restaurant name, store category, " +
                    "logo, service options, address, and operating hours before submitting.");
        }

        if (!Boolean.TRUE.equals(vendorProfile.getUser().getEmailVerified())) {
            throw new IllegalStateException(
                    "Please verify your email before submitting your vendor profile for review.");
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
     * a restaurant name, store category, logo, at least one service option, and an address.
     */
    private boolean isProfileComplete(VendorProfile profile) {
        return profile.getRestaurantName() != null && !profile.getRestaurantName().isBlank()
                && profile.getStoreCategory() != null && !profile.getStoreCategory().isBlank()
                && profile.getLogoUrl() != null && !profile.getLogoUrl().isBlank()
                && (Boolean.TRUE.equals(profile.getOffersDelivery())
                        || Boolean.TRUE.equals(profile.getOffersPickup()))
                && hasCompleteAddress(profile.getAddress())
                && profile.hasOperatingDays();
    }

    private boolean hasCompleteAddress(Address address) {
        return address != null
                && address.getAddressLine() != null && !address.getAddressLine().isBlank()
                && address.getCity() != null && !address.getCity().isBlank()
                && address.getProvince() != null
                && address.getPostalCode() != null && !address.getPostalCode().isBlank();
    }

    private <T> void updateIfNotNull(T value, java.util.function.Consumer<T> setter) {
        if (value != null) {
            setter.accept(value);
        }
    }

    private void enqueueImageCleanup(String imageUrl, String reason) {
        if (imageUrl != null && !imageUrl.isBlank()) {
            imageCleanupService.enqueue(imageUrl, reason);
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
