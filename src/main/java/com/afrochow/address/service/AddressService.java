package com.afrochow.address.service;

import com.afrochow.address.dto.AddressRequestDto;
import com.afrochow.address.dto.AddressResponseDto;
import com.afrochow.address.mapper.AddressMapper;
import com.afrochow.address.model.Address;
import com.afrochow.address.repository.AddressRepository;
import com.afrochow.customer.model.CustomerProfile;
import com.afrochow.outbox.service.OutboxEventService;
import com.afrochow.search.service.VendorGeoIndexService;
import com.afrochow.security.service.GeocodingService;
import com.afrochow.user.model.User;
import com.afrochow.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class AddressService {

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;
    private final GeocodingService geocodingService;
    private final OutboxEventService outboxEventService;
    private final VendorGeoIndexService vendorGeoIndexService;
    private final AddressMapper addressMapper;

    // Customer address use cases

    /**
     * Returns every saved delivery address for the authenticated customer.
     */
    @Transactional(readOnly = true)
    public List<AddressResponseDto> getCustomerAddresses(String publicUserId) {
        CustomerProfile profile = getCustomerProfile(getCustomerUser(publicUserId));

        return addressRepository.findByCustomerProfile(profile).stream()
                .map(addressMapper::toResponseDto)
                .toList();
    }

    /**
     * Returns one address after confirming it belongs to the authenticated customer.
     */
    @Transactional(readOnly = true)
    public AddressResponseDto getAddress(String publicUserId, String publicAddressId) {
        Address address = getAddressEntity(publicAddressId);
        assertAddressBelongsToUser(address, publicUserId);
        return addressMapper.toResponseDto(address);
    }

    @Transactional
    public AddressResponseDto addAddress(String publicUserId, AddressRequestDto request) {
        CustomerProfile profile = getCustomerProfile(getCustomerUser(publicUserId));
        Address address = buildAddress(request, profile);

        handleDefaultAddress(profile, address, true);
        address = addressRepository.save(address);
        outboxEventService.addressGeocodingRequested(address.getPublicAddressId());
        return addressMapper.toResponseDto(address);
    }

    @Transactional
    public AddressResponseDto updateAddress(
            String publicUserId,
            String publicAddressId,
            AddressRequestDto request) {

        Address address = getAddressEntity(publicAddressId);
        assertAddressBelongsToUser(address, publicUserId);
        CustomerProfile profile = getCustomerProfile(address);
        boolean needsGeocoding = shouldRequestGeocoding(address, request);

        updateEntityFromDto(request, address);
        // Do not auto-promote on update: a caller may intentionally unset the
        // default flag, which must not be silently undone.
        handleDefaultAddress(profile, address, false);

        address = addressRepository.save(address);
        if (needsGeocoding) {
            outboxEventService.addressGeocodingRequested(address.getPublicAddressId());
        }

        return addressMapper.toResponseDto(address);
    }

    @Transactional
    public void deleteAddress(String publicUserId, String publicAddressId) {
        Address address = getAddressEntity(publicAddressId);
        assertAddressBelongsToUser(address, publicUserId);

        CustomerProfile profile = getCustomerProfile(address);
        boolean wasDefault = Boolean.TRUE.equals(address.getDefaultAddress());

        addressRepository.delete(address);
        if (wasDefault) {
            setFirstAddressAsDefault(profile);
        }
    }

    @Transactional
    public AddressResponseDto setDefaultAddress(String publicUserId, String publicAddressId) {
        Address address = getAddressEntity(publicAddressId);
        assertAddressBelongsToUser(address, publicUserId);
        CustomerProfile profile = getCustomerProfile(address);

        addressRepository.unsetDefaultForCustomer(profile.getCustomerProfileId());

        address.setDefaultAddress(true);
        address = addressRepository.save(address);
        return addressMapper.toResponseDto(address);
    }

    @Transactional
    public void setFirstAddressAsDefault(CustomerProfile profile) {
        List<Address> remaining = addressRepository.findByCustomerProfile(profile);
        if (remaining.isEmpty()) {
            return;
        }

        addressRepository.unsetDefaultForCustomer(profile.getCustomerProfileId());
        Address first = remaining.getFirst();
        first.setDefaultAddress(true);
        addressRepository.save(first);
    }

    // Geocoding use cases

    /**
     * Geocode the address and attach lat/lng.
     * Logs a warning but never throws — geocoding failure
     * should not block address creation.
     */
    @Transactional
    public void geocodeAddress(String publicAddressId) {
        Address address = getAddressEntity(publicAddressId);
        geocodeAndAttach(address);
        addressRepository.save(address);

        if (address.getVendor() != null) {
            vendorGeoIndexService.indexVendor(address.getVendor());
        }
    }

    // Private helpers

    private Address buildAddress(AddressRequestDto request, CustomerProfile profile) {
        return Address.builder()
                .addressLine(request.getAddressLine())
                .city(request.getCity())
                .province(request.getProvince())
                .postalCode(request.getPostalCode())
                .country(request.getCountry())
                .defaultAddress(Boolean.TRUE.equals(request.getDefaultAddress()))
                .customerProfile(profile)
                .build();
    }

    private User getCustomerUser(String publicUserId) {
        User user = userRepository.findByPublicUserId(publicUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        if (!user.isCustomer()) {
            throw new IllegalStateException("User is not a customer");
        }
        return user;
    }

    private CustomerProfile getCustomerProfile(User user) {
        CustomerProfile profile = user.getCustomerProfile();
        if (profile == null) throw new EntityNotFoundException("Customer profile not found");
        return profile;
    }

    private CustomerProfile getCustomerProfile(Address address) {
        CustomerProfile profile = address.getCustomerProfile();
        if (profile == null) {
            throw new EntityNotFoundException("Customer profile not found");
        }
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

    private void handleDefaultAddress(CustomerProfile profile, Address address, boolean promoteIfMissing) {
        if (Boolean.TRUE.equals(address.getDefaultAddress())) {
            unsetExistingDefaultAddress(profile, address);
            return;
        }

        if (promoteIfMissing && !hasDefaultAddress(profile)) {
            address.setDefaultAddress(true);
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

    private boolean shouldRequestGeocoding(Address address, AddressRequestDto request) {
        return hasChanged(request.getAddressLine(), address.getAddressLine())
                || hasChanged(request.getCity(), address.getCity())
                || hasChanged(request.getPostalCode(), address.getPostalCode())
                || hasChanged(request.getProvince(), address.getProvince());
    }

    private boolean hasChanged(Object nextValue, Object currentValue) {
        return nextValue != null && !Objects.equals(nextValue, currentValue);
    }

    private boolean hasDefaultAddress(CustomerProfile profile) {
        return addressRepository.findByCustomerProfileAndDefaultAddress(profile, true).isPresent();
    }

    private void unsetExistingDefaultAddress(CustomerProfile profile, Address address) {
        addressRepository.findByCustomerProfileAndDefaultAddress(profile, true)
                .filter(existing -> !Objects.equals(existing.getAddressId(), address.getAddressId()))
                .ifPresent(existing -> {
                    existing.setDefaultAddress(false);
                    addressRepository.save(existing);
                });
    }

    private void geocodeAndAttach(Address address) {
        try {
            String formatted = address.getFormattedAddress();
            double[] coords = geocodingService.geocode(formatted);

            if (coords == null) {
                log.warn("Geocoding returned no result for address: {}", formatted);
                return;
            }

            address.setLatitude(coords[0]);
            address.setLongitude(coords[1]);
            log.debug("Geocoded address '{}' to lat={}, lng={}", formatted, coords[0], coords[1]);
        } catch (Exception e) {
            log.warn("Geocoding failed, address saved without coordinates", e);
        }
    }
}
