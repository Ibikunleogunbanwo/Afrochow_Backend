package com.afrochow.address.service;

import com.afrochow.address.dto.AddressRequestDto;
import com.afrochow.address.dto.AddressResponseDto;
import com.afrochow.address.model.Address;
import com.afrochow.address.repository.AddressRepository;
import com.afrochow.customer.model.CustomerProfile;
import com.afrochow.security.Utils.GeocodingService;
import com.afrochow.user.model.User;
import com.afrochow.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AddressService {

    private final AddressRepository  addressRepository;
    private final UserRepository     userRepository;
    private final GeocodingService geocodingService;

    // ── Public methods ────────────────────────────────────────────────────────

    public List<AddressResponseDto> getCustomerAddresses(String publicUserId) {
        User user = getCustomerUser(publicUserId);
        CustomerProfile profile = getCustomerProfile(user);

        return addressRepository.findByCustomerProfile(profile).stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    public AddressResponseDto getAddress(String publicUserId, String publicAddressId) {
        Address address = getAddressEntity(publicAddressId);
        assertAddressBelongsToUser(address, publicUserId);
        return toResponseDto(address);
    }

    @Transactional
    public AddressResponseDto addAddress(String publicUserId, AddressRequestDto request) {
        User user = getCustomerUser(publicUserId);
        CustomerProfile profile = getCustomerProfile(user);

        Address address = Address.builder()
                .addressLine(request.getAddressLine())
                .city(request.getCity())
                .province(request.getProvince())
                .postalCode(request.getPostalCode())
                .country(request.getCountry())
                .defaultAddress(request.getDefaultAddress() != null
                        ? request.getDefaultAddress() : false)
                .customerProfile(profile)
                .build();

        geocodeAndAttach(address);
        handleDefaultAddress(profile, address);
        addressRepository.save(address);
        return toResponseDto(address);
    }

    @Transactional
    public AddressResponseDto updateAddress(
            String publicUserId,
            String publicAddressId,
            AddressRequestDto request) {

        Address address = getAddressEntity(publicAddressId);
        assertAddressBelongsToUser(address, publicUserId);
        CustomerProfile profile = getCustomerProfile(
                address.getCustomerProfile().getUser());

        boolean addressLineChanged = request.getAddressLine() != null
                && !request.getAddressLine().equals(address.getAddressLine());
        boolean cityChanged = request.getCity() != null
                && !request.getCity().equals(address.getCity());

        updateEntityFromDto(request, address);

        // Re-geocode only if the physical address changed
        if (addressLineChanged || cityChanged) {
            geocodeAndAttach(address);
        }

        handleDefaultAddress(profile, address);
        address = addressRepository.save(address);
        return toResponseDto(address);
    }

    @Transactional
    public void deleteAddress(String publicUserId, String publicAddressId) {
        Address address = getAddressEntity(publicAddressId);
        assertAddressBelongsToUser(address, publicUserId);

        CustomerProfile profile   = address.getCustomerProfile();
        boolean         wasDefault = address.getDefaultAddress();

        addressRepository.delete(address);
        if (wasDefault) {
            setFirstAddressAsDefault(profile);
        }
    }

    @Transactional
    public AddressResponseDto setDefaultAddress(String publicUserId, String publicAddressId) {
        Address address = getAddressEntity(publicAddressId);
        assertAddressBelongsToUser(address, publicUserId);

        CustomerProfile profile = getCustomerProfile(
                address.getCustomerProfile().getUser());

        addressRepository.unsetDefaultForCustomer(profile.getCustomerProfileId());

        address.setDefaultAddress(true);
        address = addressRepository.save(address);
        return toResponseDto(address);
    }

    @Transactional
    public void setFirstAddressAsDefault(CustomerProfile profile) {
        List<Address> remaining = addressRepository.findByCustomerProfile(profile);
        if (remaining.isEmpty()) return;

        addressRepository.unsetDefaultForCustomer(profile.getCustomerProfileId());
        Address first = remaining.getFirst();
        first.setDefaultAddress(true);
        addressRepository.save(first);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Geocode the address and attach lat/lng.
     * Logs a warning but never throws — geocoding failure
     * should not block address creation.
     */
    private void geocodeAndAttach(Address address) {
        try {
            String formatted = address.getFormattedAddress();
            double[] coords  = geocodingService.geocode(formatted);
            if (coords != null) {
                address.setLatitude(coords[0]);
                address.setLongitude(coords[1]);
                log.debug("Geocoded address '{}' → lat={}, lng={}",
                        formatted, coords[0], coords[1]);
            } else {
                log.warn("Geocoding returned no result for address: {}", formatted);
            }
        } catch (Exception e) {
            log.warn("Geocoding failed, address saved without coordinates", e);
        }
    }

    private User getCustomerUser(String publicUserId) {
        User user = userRepository.findByPublicUserId(publicUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        if (!user.isCustomer()) throw new IllegalStateException("User is not a customer");
        return user;
    }

    private CustomerProfile getCustomerProfile(User user) {
        CustomerProfile profile = user.getCustomerProfile();
        if (profile == null) throw new EntityNotFoundException("Customer profile not found");
        return profile;
    }

    private Address getAddressEntity(String publicAddressId) {
        return addressRepository.findByPublicAddressId(publicAddressId)
                .orElseThrow(() -> new EntityNotFoundException("Address not found"));
    }

    private void assertAddressBelongsToUser(Address address, String publicUserId) {
        if (address.getCustomerProfile() == null ||
                !address.getCustomerProfile().getUser()
                        .getPublicUserId().equals(publicUserId)) {
            throw new IllegalStateException("Address does not belong to this customer");
        }
    }

    private void handleDefaultAddress(CustomerProfile profile, Address address) {
        if (Boolean.TRUE.equals(address.getDefaultAddress())) {
            addressRepository.findByCustomerProfileAndDefaultAddress(profile, true)
                    .ifPresent(existing -> {
                        if (!existing.getAddressId().equals(address.getAddressId())) {
                            existing.setDefaultAddress(false);
                            addressRepository.save(existing);
                        }
                    });
        } else {
            boolean hasDefault = addressRepository
                    .findByCustomerProfileAndDefaultAddress(profile, true)
                    .isPresent();
            if (!hasDefault) {
                address.setDefaultAddress(true);
            }
        }
    }

    private void updateEntityFromDto(AddressRequestDto dto, Address address) {
        if (dto.getAddressLine()   != null) address.setAddressLine(dto.getAddressLine());
        if (dto.getCity()          != null) address.setCity(dto.getCity());
        if (dto.getProvince()      != null) address.setProvince(dto.getProvince());
        if (dto.getPostalCode()    != null) address.setPostalCode(dto.getPostalCode());
        if (dto.getCountry()       != null) address.setCountry(dto.getCountry());
        if (dto.getDefaultAddress() != null) address.setDefaultAddress(dto.getDefaultAddress());
    }

    private AddressResponseDto toResponseDto(Address address) {
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