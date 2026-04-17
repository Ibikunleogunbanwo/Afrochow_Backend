package com.afrochow.customer.service;

import com.afrochow.address.dto.AddressResponseDto;
import com.afrochow.address.model.Address;
import com.afrochow.address.dto.AddressRequestDto;
import com.afrochow.customer.dto.CompleteProfileRequestDto;
import com.afrochow.customer.dto.CustomerPasswordUpdate;
import com.afrochow.customer.dto.CustomerUpdateRequestDto;
import com.afrochow.customer.dto.CustomerProfileResponseDto;
import com.afrochow.customer.model.CustomerProfile;
import com.afrochow.customer.repository.CustomerProfileRepository;
import com.afrochow.common.validation.PhoneUtils;
import com.afrochow.image.ImageUploadService;
import com.afrochow.security.Services.PasswordPolicyService;
import com.afrochow.security.model.CustomUserDetails;
import com.afrochow.user.model.User;
import com.afrochow.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Service for managing customer profiles.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerProfileService {

    private final CustomerProfileRepository customerProfileRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ImageUploadService imageUploadService;
    private final PasswordPolicyService passwordPolicyService;


    /* ---------------------------------------------------------- */
    /*  READ                                                     */
    /* ---------------------------------------------------------- */
    @Transactional(readOnly = true)
    public CustomerProfileResponseDto getProfile(String publicUserId) {

        User user = userRepository.findByPublicUserId(publicUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        if (!user.isCustomer()) {
            throw new IllegalStateException("User is not a customer");
        }

        CustomerProfile profile = user.getCustomerProfile();
        if (profile == null) {
            throw new EntityNotFoundException("Customer profile not found");
        }

        return toResponseDto(profile);
    }


    /* ---------------------------------------------------------- */
    /*  UPDATE PROFILE                                            */
    /* ---------------------------------------------------------- */
    @Transactional
    public CustomerProfileResponseDto updateProfile(String publicUserId, CustomerUpdateRequestDto dto) {

        User user = userRepository.findByPublicUserId(publicUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        if (!user.isCustomer()) {
            throw new IllegalStateException("User is not a customer");
        }

        CustomerProfile profile = Optional.ofNullable(user.getCustomerProfile())
                .orElseThrow(() -> new EntityNotFoundException("Customer profile not found"));

        // -----------------------------
        // Debug log for profileImageUrl
        // -----------------------------
        log.debug("Received profileImageUrl from frontend: {}", dto.getProfileImageUrl());

        Optional.ofNullable(dto.getProfileImageUrl())
                .filter(s -> !s.isBlank())
                .ifPresent(url -> {
                    user.setProfileImageUrl(url);
                    log.debug("Set profileImageUrl on User entity: {}", url);
                });

        // Update other fields as needed...
        Optional.ofNullable(dto.getFirstName()).filter(s -> !s.isBlank()).ifPresent(user::setFirstName);
        Optional.ofNullable(dto.getLastName()).filter(s -> !s.isBlank()).ifPresent(user::setLastName);

        // Phone update: enforce uniqueness. A repeat submission of the SAME phone
        // must not throw — only a phone already belonging to a different user should.
        Optional.ofNullable(dto.getPhone()).filter(s -> !s.isBlank())
                .map(PhoneUtils::normalize)
                .ifPresent(normalizedPhone -> {
                    if (!normalizedPhone.equals(user.getPhone())) {
                        userRepository.findByPhone(normalizedPhone).ifPresent(existing -> {
                            if (!existing.getPublicUserId().equals(user.getPublicUserId())) {
                                throw new IllegalStateException("Phone number already in use");
                            }
                        });
                        user.setPhone(normalizedPhone);
                    }
                });

        // NOTE: email change intentionally not handled here. The canonical path is
        // PUT /user/profile (UserController), which is expected to gain a verification
        // step. Accepting email changes in two places invites drift + audit gaps.

        // Update CustomerProfile fields
        Optional.ofNullable(dto.getDefaultDeliveryInstructions()).ifPresent(profile::setDefaultDeliveryInstructions);
        Optional.ofNullable(dto.getPaymentMethod()).ifPresent(profile::setPaymentMethod);

        // Save entities
        userRepository.save(user);
        customerProfileRepository.save(profile);

        log.debug("Saved User entity with profileImageUrl: {}", user.getProfileImageUrl());

        return toResponseDto(profile);
    }


    /**
     * Helper: update value if present and not blank (for String) or not null (for other types)
     */
    private <T> void updateIfPresent(T value, Consumer<T> setter) {
        if (value == null) return;

        if (value instanceof String s) {
            if (!s.isBlank()) setter.accept(value);
        } else {
            setter.accept(value);
        }
    }



    /* ---------------------------------------------------------- */
    /*  NOTIFICATION PREFERENCES                                 */
    /* ---------------------------------------------------------- */
    @Transactional
    public CustomerProfileResponseDto updateNotificationPreference(String publicUserId, boolean enabled) {
        User user = userRepository.findByPublicUserId(publicUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        if (!user.isCustomer()) {
            throw new IllegalStateException("User is not a customer");
        }

        CustomerProfile profile = Optional.ofNullable(user.getCustomerProfile())
                .orElseThrow(() -> new EntityNotFoundException("Customer profile not found"));

        profile.setNotificationsEnabled(enabled);
        customerProfileRepository.save(profile);

        return toResponseDto(profile);
    }

    /* ---------------------------------------------------------- */
    /*  UPDATE PASSWORD                                           */
    /* ---------------------------------------------------------- */
    @Transactional
    public void updatePassword(String publicUserId, CustomerPasswordUpdate dto) {
        User user = userRepository.findByPublicUserId(publicUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        if (!user.isCustomer()) {
            throw new IllegalStateException("User is not a customer");
        }

        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Old password is incorrect");
        }

        if (!dto.isPasswordMatching()) {
            throw new IllegalArgumentException("New passwords do not match");
        }

        // Belt-and-suspenders: DTO @Pattern already checks char classes, but we also
        // run the authoritative policy so this endpoint behaves identically to
        // /auth/change-password if the regexes ever drift again.
        passwordPolicyService.validatePassword(dto.getNewPassword());

        // Reject re-using the current password (parity with AuthenticationService.changePassword)
        if (passwordEncoder.matches(dto.getNewPassword(), user.getPassword())) {
            throw new IllegalArgumentException("New password must be different from the current password");
        }

        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userRepository.save(user);
    }



    /* ---------------------------------------------------------- */
    /*  IMAGE UPLOAD                                             */
    /* ---------------------------------------------------------- */
    @Transactional
    public CustomerProfileResponseDto uploadProfileImage(MultipartFile file,
                                                         CustomUserDetails currentUser) throws IOException {

        // Every other customer endpoint keys off publicUserId; keeping a stray
        // findById() here created a subtle auth-surface asymmetry (numeric
        // internal ID vs the opaque public ID everyone else uses).
        User user = userRepository.findByPublicUserId(currentUser.getPublicUserId())
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        if (!user.isCustomer()) {
            throw new IllegalStateException("User is not a customer");
        }
        CustomerProfile profile = user.getCustomerProfile();
        if (profile == null) {          // should never happen
            throw new EntityNotFoundException("Customer profile not found");
        }

        /* delete previous image — but only if it looks like one WE uploaded.
         * Google-registered users often have an https://lh3.googleusercontent.com
         * avatar; we must never try to delete those from our storage. */
        String oldImage = user.getProfileImageUrl();
        if (isSelfHostedImage(oldImage)) {
            try {
                imageUploadService.deleteImage(oldImage);
            } catch (Exception e) {
                // Don't block the new upload just because cleanup failed — the
                // old file becomes an orphan, not a hard error.
                log.warn("Failed to delete previous profile image {}: {}", oldImage, e.getMessage());
            }
        }

        /* store new one */
        String newPath = imageUploadService.uploadImageForRegistrationAndGetUrl(file, "customer/profile_image");
        user.setProfileImageUrl(newPath);
        userRepository.save(user);

        return toResponseDto(profile);
    }

    /**
     * An image URL is "self-hosted" if it lives inside our own storage
     * namespace. Anything else (e.g. Google avatars) must be left alone.
     */
    private boolean isSelfHostedImage(String url) {
        if (url == null || url.isBlank()) return false;
        // Relative paths like "customer/profile_image/abc.jpg" are ours.
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return true;
        }
        // Absolute URLs: allow only if the path begins with one of our buckets/prefixes.
        // We match loosely on well-known internal prefixes to avoid coupling to an
        // environment-specific CDN host.
        return url.contains("/customer/profile_image/")
                || url.contains("/vendor/profile_image/")
                || url.contains("/afrochow-uploads/");
    }



    /* ========================================================== */
    /*  Helpers                                                   */
    /* ========================================================== */
    private void updateEntityFromDto(CustomerUpdateRequestDto src, CustomerProfile target) {
        /* map only the fields that belong to CustomerProfile */
        if (src.getPaymentMethod() != null) {
            target.setPaymentMethod(src.getPaymentMethod());
        }
        /* add more if needed … */
    }

    /* ---------------------------------------------------------- */
    /*  COMPLETE GOOGLE PROFILE (onboarding)                     */
    /* ---------------------------------------------------------- */

    /**
     * Called once after Google OAuth auto-creation to fill in the missing
     * required fields (phone, and optionally address + username).
     * Idempotent — safe to call again if the user refreshes mid-onboarding.
     */
    @Transactional
    public CustomerProfileResponseDto completeProfile(String publicUserId, CompleteProfileRequestDto dto) {
        User user = userRepository.findByPublicUserId(publicUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        if (!user.isCustomer()) {
            throw new IllegalStateException("Only customer accounts can complete a profile here");
        }

        CustomerProfile profile = Optional.ofNullable(user.getCustomerProfile())
                .orElseThrow(() -> new EntityNotFoundException("Customer profile not found"));

        // Name — optional, overrides the Google-provided name if the user prefers different values
        if (dto.getFirstName() != null && !dto.getFirstName().isBlank()) {
            user.setFirstName(dto.getFirstName().trim());
        }
        if (dto.getLastName() != null && !dto.getLastName().isBlank()) {
            user.setLastName(dto.getLastName().trim());
        }

        // Phone — always required
        String normalizedPhone = PhoneUtils.normalize(dto.getPhone());
        if (userRepository.existsByPhone(normalizedPhone) &&
                !normalizedPhone.equals(user.getPhone())) {
            throw new IllegalArgumentException("Phone number is already registered to another account");
        }
        user.setPhone(normalizedPhone);

        // Username — optional, set only if provided and not already taken
        if (dto.getUsername() != null && !dto.getUsername().isBlank()) {
            String username = dto.getUsername().trim();
            if (userRepository.existsByUsername(username) &&
                    !username.equals(user.getUsername())) {
                throw new IllegalArgumentException("Username is already taken");
            }
            user.setUsername(username);
        }

        // Delivery instructions — optional
        if (dto.getDefaultDeliveryInstructions() != null) {
            profile.setDefaultDeliveryInstructions(dto.getDefaultDeliveryInstructions());
        }

        // Address — optional at this stage (can be added at checkout)
        if (dto.getAddress() != null) {
            AddressRequestDto a = dto.getAddress();
            Address address = Address.builder()
                    .addressLine(a.getAddressLine())
                    .city(a.getCity())
                    .province(a.getProvince())
                    .postalCode(a.getPostalCode())
                    .country(a.getCountry() != null ? a.getCountry() : "Canada")
                    .defaultAddress(Boolean.TRUE)
                    .build();
            profile.addAddress(address);
        }

        userRepository.save(user);
        log.info("google.profile.complete publicUserId={}", publicUserId);
        return toResponseDto(profile);
    }

    private CustomerProfileResponseDto toResponseDto(CustomerProfile profile) {
        User user = profile.getUser();
        if (user == null) {
            throw new IllegalStateException("Customer profile has no associated user");
        }

        List<AddressResponseDto> addresses = profile.getAddresses() == null
                ? List.of()
                : profile.getAddresses().stream()
                .map(this::toAddressResponseDto)
                .toList();

        boolean profileComplete = user.getPhone() != null && !user.getPhone().isBlank();
        String authProvider = user.getAuthProvider() != null ? user.getAuthProvider().name() : "EMAIL";

        return CustomerProfileResponseDto.builder()
                .publicUserId(user.getPublicUserId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .profileImageUrl(user.getProfileImageUrl())
                .loyaltyPoints(profile.getLoyaltyPoints())
                .defaultDeliveryInstructions(profile.getDefaultDeliveryInstructions())
                .totalOrders(profile.getTotalOrders())
                .addresses(addresses)
                .notificationsEnabled(profile.getNotificationsEnabled())
                .createdAt(profile.getCreatedAt())
                .isProfileComplete(profileComplete)
                .authProvider(authProvider)
                .build();
    }


    private AddressResponseDto toAddressResponseDto(Address address) {
        if (address == null) {
            return null;
        }

        return AddressResponseDto.builder()
                .publicAddressId(address.getPublicAddressId())
                .addressLine(address.getAddressLine())
                .city(address.getCity())
                .province(address.getProvince())
                .postalCode(address.getPostalCode())
                .country(address.getCountry())
                .defaultAddress(address.getDefaultAddress())
                .formattedAddress(address.getFormattedAddress())
                .createdAt(address.getCreatedAt())
                .updatedAt(address.getUpdatedAt())
                .build();
    }


}