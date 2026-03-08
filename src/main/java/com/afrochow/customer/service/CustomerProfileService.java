package com.afrochow.customer.service;

import com.afrochow.address.dto.AddressResponseDto;
import com.afrochow.address.model.Address;
import com.afrochow.customer.dto.CustomerPasswordUpdate;
import com.afrochow.customer.dto.CustomerUpdateRequestDto;
import com.afrochow.customer.dto.CustomerProfileResponseDto;
import com.afrochow.customer.model.CustomerProfile;
import com.afrochow.customer.repository.CustomerProfileRepository;
import com.afrochow.image.ImageUploadService;
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
        Optional.ofNullable(dto.getPhone()).filter(s -> !s.isBlank()).ifPresent(user::setPhone);
        Optional.ofNullable(dto.getEmail()).filter(s -> !s.isBlank()).ifPresent(email -> {
            if (!email.equalsIgnoreCase(user.getEmail())) {
                userRepository.findByEmail(email).ifPresent(u -> {
                    throw new IllegalStateException("Email already in use");
                });
                user.setEmail(email);
            }
        });

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

        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userRepository.save(user);
    }



    /* ---------------------------------------------------------- */
    /*  IMAGE UPLOAD                                             */
    /* ---------------------------------------------------------- */
    @Transactional
    public CustomerProfileResponseDto uploadProfileImage(MultipartFile file,
                                                         CustomUserDetails currentUser) throws IOException {

        User user = userRepository.findById(currentUser.getUserId())
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        if (!user.isCustomer()) {
            throw new IllegalStateException("User is not a customer");
        }
        CustomerProfile profile = user.getCustomerProfile();
        if (profile == null) {          // should never happen
            throw new EntityNotFoundException("Customer profile not found");
        }

        /* delete previous image */
        String oldImage = user.getProfileImageUrl();
        if (oldImage != null && !oldImage.isBlank()) {
            imageUploadService.deleteImage(oldImage);
        }

        /* store new one */
        String newPath = imageUploadService.uploadImageForRegistrationAndGetUrl(file, "customer/profile_image");
        user.setProfileImageUrl(newPath);
        userRepository.save(user);

        return toResponseDto(profile);
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
                .createdAt(profile.getCreatedAt())
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