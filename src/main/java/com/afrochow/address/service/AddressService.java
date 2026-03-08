package com.afrochow.address.service;

import com.afrochow.address.dto.AddressRequestDto;
import com.afrochow.address.dto.AddressResponseDto;
import com.afrochow.address.model.Address;
import com.afrochow.address.repository.AddressRepository;
import com.afrochow.customer.model.CustomerProfile;
import com.afrochow.user.model.User;
import com.afrochow.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AddressService {

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;

    public AddressService(AddressRepository addressRepository, UserRepository userRepository) {
        this.addressRepository = addressRepository;
        this.userRepository = userRepository;
    }

    // ------------------------
    // Public methods
    // ------------------------

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
                .defaultAddress(request.getDefaultAddress() != null ? request.getDefaultAddress() : false)
                .customerProfile(profile)
                .build();

        handleDefaultAddress(profile, address);

        addressRepository.save(address);
        return toResponseDto(address);
    }

    @Transactional
    public AddressResponseDto updateAddress(String publicUserId, String publicAddressId, AddressRequestDto request) {
        Address address = getAddressEntity(publicAddressId);
        assertAddressBelongsToUser(address, publicUserId);
        CustomerProfile profile = getCustomerProfile(address.getCustomerProfile().getUser());

        updateEntityFromDto(request, address);
        handleDefaultAddress(profile, address);

        address = addressRepository.save(address);
        return toResponseDto(address);
    }

    @Transactional
    public void deleteAddress(String publicUserId, String publicAddressId) {
        Address address = getAddressEntity(publicAddressId);
        assertAddressBelongsToUser(address, publicUserId);

        CustomerProfile profile = address.getCustomerProfile();
        boolean wasDefault = address.getDefaultAddress();

        addressRepository.delete(address);
        if (wasDefault) {
            setFirstAddressAsDefault(profile);
        }
    }

    @Transactional
    public AddressResponseDto setDefaultAddress(String publicUserId, String publicAddressId) {
        Address address = getAddressEntity(publicAddressId);

        assertAddressBelongsToUser(address, publicUserId);

        CustomerProfile profile = getCustomerProfile(address.getCustomerProfile().getUser());

        addressRepository.unsetDefaultForCustomer(profile.getCustomerProfileId());

        address.setDefaultAddress(true);
        address = addressRepository.save(address);

        return toResponseDto(address);
    }



    // ------------------------
    // Private helpers
    // ------------------------

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
                !address.getCustomerProfile().getUser().getPublicUserId().equals(publicUserId)) {
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
            boolean hasDefault = addressRepository.findByCustomerProfileAndDefaultAddress(profile, true).isPresent();
            if (!hasDefault) {
                address.setDefaultAddress(true);
            }
        }
    }

    @Transactional
    public void setFirstAddressAsDefault(CustomerProfile profile) {
        List<Address> remainingAddresses = addressRepository.findByCustomerProfile(profile);

        if (remainingAddresses.isEmpty()) return;
        addressRepository.unsetDefaultForCustomer(profile.getCustomerProfileId());

        Address firstAddress = remainingAddresses.getFirst();
        firstAddress.setDefaultAddress(true);
        addressRepository.save(firstAddress);
    }


    private void updateEntityFromDto(AddressRequestDto dto, Address address) {
        if (dto.getAddressLine() != null) address.setAddressLine(dto.getAddressLine());
        if (dto.getCity() != null) address.setCity(dto.getCity());
        if (dto.getProvince() != null) address.setProvince(dto.getProvince());
        if (dto.getPostalCode() != null) address.setPostalCode(dto.getPostalCode());
        if (dto.getCountry() != null) address.setCountry(dto.getCountry());
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
                .formattedAddress(address.getFormattedAddress()) // uses entity getter
                .createdAt(address.getCreatedAt())
                .updatedAt(address.getUpdatedAt())
                .build();
    }
}
