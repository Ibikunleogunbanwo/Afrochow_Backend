package com.afrochow.address.service;

import com.afrochow.address.dto.AddressRequestDto;
import com.afrochow.address.dto.AddressResponseDto;
import com.afrochow.address.mapper.AddressMapper;
import com.afrochow.address.model.Address;
import com.afrochow.address.repository.AddressRepository;
import com.afrochow.common.enums.Province;
import com.afrochow.common.enums.Role;
import com.afrochow.customer.model.CustomerProfile;
import com.afrochow.outbox.service.OutboxEventService;
import com.afrochow.search.VendorGeoIndexService;
import com.afrochow.security.Utils.GeocodingService;
import com.afrochow.user.model.User;
import com.afrochow.user.repository.UserRepository;
import com.afrochow.vendor.model.VendorProfile;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

    @Mock private AddressRepository addressRepository;
    @Mock private UserRepository userRepository;
    @Mock private GeocodingService geocodingService;
    @Mock private OutboxEventService outboxEventService;
    @Mock private VendorGeoIndexService vendorGeoIndexService;
    @Spy private AddressMapper addressMapper;

    @InjectMocks private AddressService addressService;

    private User customer;
    private CustomerProfile profile;
    private Address address;

    @BeforeEach
    void setUp() {
        customer = User.builder().userId(1L).publicUserId("CUS1").role(Role.CUSTOMER).build();
        profile = CustomerProfile.builder().customerProfileId(10L).user(customer).build();
        customer.setCustomerProfile(profile);
        address = Address.builder().addressId(100L).publicAddressId("ADDR1")
                .addressLine("123 Main St").city("Calgary").province(Province.AB)
                .postalCode("T2P1J9").country("Canada").defaultAddress(true)
                .customerProfile(profile).build();
    }

    // ========== getCustomerAddresses ==========

    @Test
    void getCustomerAddresses_returnsAllForCustomer() {
        when(userRepository.findByPublicUserId("CUS1")).thenReturn(Optional.of(customer));
        when(addressRepository.findByCustomerProfile(profile)).thenReturn(List.of(address));

        List<AddressResponseDto> result = addressService.getCustomerAddresses("CUS1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCity()).isEqualTo("Calgary");
    }

    @Test
    void getCustomerAddresses_nonCustomer_throwsIllegalState() {
        User vendor = User.builder().userId(2L).publicUserId("VEN1").role(Role.VENDOR).build();
        when(userRepository.findByPublicUserId("VEN1")).thenReturn(Optional.of(vendor));

        assertThatThrownBy(() -> addressService.getCustomerAddresses("VEN1"))
                .isInstanceOf(IllegalStateException.class);
    }

    // ========== getAddress ==========

    @Test
    void getAddress_ownedByCaller_returnsDto() {
        when(addressRepository.findByPublicAddressId("ADDR1")).thenReturn(Optional.of(address));

        AddressResponseDto result = addressService.getAddress("CUS1", "ADDR1");

        assertThat(result.getPublicAddressId()).isEqualTo("ADDR1");
    }

    @Test
    void getAddress_notOwnedByCaller_throwsIllegalState() {
        when(addressRepository.findByPublicAddressId("ADDR1")).thenReturn(Optional.of(address));

        assertThatThrownBy(() -> addressService.getAddress("someoneelse", "ADDR1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not belong");
    }

    @Test
    void getAddress_notFound_throwsEntityNotFound() {
        when(addressRepository.findByPublicAddressId("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.getAddress("CUS1", "missing"))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ========== addAddress ==========

    @Test
    void addAddress_firstAddress_becomesDefaultAndFiresGeocodeEvent() {
        when(userRepository.findByPublicUserId("CUS1")).thenReturn(Optional.of(customer));
        when(addressRepository.findByCustomerProfileAndDefaultAddress(profile, true)).thenReturn(Optional.empty());
        when(addressRepository.save(any(Address.class))).thenAnswer(inv -> {
            Address a = inv.getArgument(0);
            a.setPublicAddressId("ADDR-NEW");
            return a;
        });
        AddressRequestDto request = AddressRequestDto.builder()
                .addressLine("456 New St").city("Calgary").province(Province.AB)
                .postalCode("T2P1J9").build();

        AddressResponseDto result = addressService.addAddress("CUS1", request);

        assertThat(result.getDefaultAddress()).isTrue(); // auto-promoted since no existing default
        verify(outboxEventService).addressGeocodingRequested("ADDR-NEW");
    }

    @Test
    void addAddress_explicitDefault_unsetsPreviousDefault() {
        Address existingDefault = Address.builder().addressId(99L).publicAddressId("ADDR-OLD")
                .customerProfile(profile).defaultAddress(true).build();
        when(userRepository.findByPublicUserId("CUS1")).thenReturn(Optional.of(customer));
        when(addressRepository.findByCustomerProfileAndDefaultAddress(profile, true))
                .thenReturn(Optional.of(existingDefault));
        when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));
        AddressRequestDto request = AddressRequestDto.builder()
                .addressLine("456 New St").city("Calgary").province(Province.AB)
                .postalCode("T2P1J9").defaultAddress(true).build();

        addressService.addAddress("CUS1", request);

        assertThat(existingDefault.getDefaultAddress()).isFalse();
        verify(addressRepository).save(existingDefault);
    }

    @Test
    void addAddress_notDefaultButAlreadyHasDefault_staysNonDefault() {
        Address existingDefault = Address.builder().addressId(99L).publicAddressId("ADDR-OLD")
                .customerProfile(profile).defaultAddress(true).build();
        when(userRepository.findByPublicUserId("CUS1")).thenReturn(Optional.of(customer));
        when(addressRepository.findByCustomerProfileAndDefaultAddress(profile, true))
                .thenReturn(Optional.of(existingDefault));
        when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));
        AddressRequestDto request = AddressRequestDto.builder()
                .addressLine("456 New St").city("Calgary").province(Province.AB)
                .postalCode("T2P1J9").defaultAddress(false).build();

        AddressResponseDto result = addressService.addAddress("CUS1", request);

        assertThat(result.getDefaultAddress()).isFalse();
    }

    // ========== updateAddress ==========

    @Test
    void updateAddress_addressLineChanged_firesGeocodeEvent() {
        when(addressRepository.findByPublicAddressId("ADDR1")).thenReturn(Optional.of(address));
        when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));
        AddressRequestDto request = AddressRequestDto.builder()
                .addressLine("789 Changed Ave").city("Calgary").province(Province.AB)
                .postalCode("T2P1J9").build();

        addressService.updateAddress("CUS1", "ADDR1", request);

        verify(outboxEventService).addressGeocodingRequested("ADDR1");
    }

    @Test
    void updateAddress_noGeoRelevantChange_doesNotFireGeocodeEvent() {
        when(addressRepository.findByPublicAddressId("ADDR1")).thenReturn(Optional.of(address));
        when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));
        AddressRequestDto request = AddressRequestDto.builder()
                .addressLine("123 Main St").city("Calgary").province(Province.AB)
                .postalCode("T2P1J9").build();

        addressService.updateAddress("CUS1", "ADDR1", request);

        verify(outboxEventService, never()).addressGeocodingRequested(any());
    }

    @Test
    void updateAddress_unsetDefault_staysNonDefault() {
        when(addressRepository.findByPublicAddressId("ADDR1")).thenReturn(Optional.of(address));
        when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));
        AddressRequestDto request = AddressRequestDto.builder()
                .addressLine("123 Main St").city("Calgary").province(Province.AB)
                .postalCode("T2P1J9").defaultAddress(false).build();

        AddressResponseDto result = addressService.updateAddress("CUS1", "ADDR1", request);

        assertThat(result.getDefaultAddress()).isFalse();
    }

    @Test
    void updateAddress_notOwnedByCaller_throwsIllegalState() {
        when(addressRepository.findByPublicAddressId("ADDR1")).thenReturn(Optional.of(address));

        assertThatThrownBy(() -> addressService.updateAddress("someoneelse", "ADDR1",
                AddressRequestDto.builder().build()))
                .isInstanceOf(IllegalStateException.class);
    }

    // ========== deleteAddress ==========

    @Test
    void deleteAddress_wasDefault_promotesAnotherAddressToDefault() {
        Address remaining = Address.builder().addressId(101L).publicAddressId("ADDR2")
                .customerProfile(profile).defaultAddress(false).build();
        when(addressRepository.findByPublicAddressId("ADDR1")).thenReturn(Optional.of(address));
        when(addressRepository.findByCustomerProfile(profile)).thenReturn(List.of(remaining));

        addressService.deleteAddress("CUS1", "ADDR1");

        verify(addressRepository).delete(address);
        verify(addressRepository).unsetDefaultForCustomer(10L);
        assertThat(remaining.getDefaultAddress()).isTrue();
        verify(addressRepository).save(remaining);
    }

    @Test
    void deleteAddress_wasNotDefault_doesNotPromoteAnything() {
        address.setDefaultAddress(false);
        when(addressRepository.findByPublicAddressId("ADDR1")).thenReturn(Optional.of(address));

        addressService.deleteAddress("CUS1", "ADDR1");

        verify(addressRepository).delete(address);
        verify(addressRepository, never()).findByCustomerProfile(any());
    }

    @Test
    void deleteAddress_lastRemainingWasDefault_noAddressesLeft_noPromotion() {
        when(addressRepository.findByPublicAddressId("ADDR1")).thenReturn(Optional.of(address));
        when(addressRepository.findByCustomerProfile(profile)).thenReturn(List.of());

        addressService.deleteAddress("CUS1", "ADDR1");

        verify(addressRepository, never()).unsetDefaultForCustomer(any());
    }

    // ========== setDefaultAddress ==========

    @Test
    void setDefaultAddress_unsetsOthersAndSetsThisOne() {
        address.setDefaultAddress(false);
        when(addressRepository.findByPublicAddressId("ADDR1")).thenReturn(Optional.of(address));
        when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

        AddressResponseDto result = addressService.setDefaultAddress("CUS1", "ADDR1");

        verify(addressRepository).unsetDefaultForCustomer(10L);
        assertThat(result.getDefaultAddress()).isTrue();
    }

    // ========== geocodeAddress ==========

    @Test
    void geocodeAddress_success_setsLatLngAndIndexesVendor() {
        VendorProfile vendor = VendorProfile.builder().id(1L).address(address).build();
        address.setVendor(vendor);
        when(addressRepository.findByPublicAddressId("ADDR1")).thenReturn(Optional.of(address));
        when(geocodingService.geocode(any())).thenReturn(new double[]{51.05, -114.07});
        when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

        addressService.geocodeAddress("ADDR1");

        assertThat(address.getLatitude()).isEqualTo(51.05);
        assertThat(address.getLongitude()).isEqualTo(-114.07);
        verify(vendorGeoIndexService).indexVendor(vendor);
    }

    @Test
    void geocodeAddress_noResult_savesWithoutCoordinates() {
        when(addressRepository.findByPublicAddressId("ADDR1")).thenReturn(Optional.of(address));
        when(geocodingService.geocode(any())).thenReturn(null);
        when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

        addressService.geocodeAddress("ADDR1");

        assertThat(address.getLatitude()).isNull();
        verify(vendorGeoIndexService, never()).indexVendor(any());
    }

    @Test
    void geocodeAddress_geocodingThrows_doesNotPropagate() {
        when(addressRepository.findByPublicAddressId("ADDR1")).thenReturn(Optional.of(address));
        when(geocodingService.geocode(any())).thenThrow(new RuntimeException("API down"));
        when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

        addressService.geocodeAddress("ADDR1"); // should not throw

        verify(addressRepository).save(address);
    }

    // ========== setFirstAddressAsDefault ==========

    @Test
    void setFirstAddressAsDefault_noRemainingAddresses_noOp() {
        when(addressRepository.findByCustomerProfile(profile)).thenReturn(List.of());

        addressService.setFirstAddressAsDefault(profile);

        verify(addressRepository, never()).unsetDefaultForCustomer(any());
        verify(addressRepository, never()).save(any());
    }
}
