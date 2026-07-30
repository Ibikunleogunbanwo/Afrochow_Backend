package com.afrochow.vendor.service;

import com.afrochow.address.dto.AddressRequestDto;
import com.afrochow.address.dto.AddressResponseDto;
import com.afrochow.address.model.Address;
import com.afrochow.address.repository.AddressRepository;
import com.afrochow.common.enums.Province;
import com.afrochow.common.enums.Role;
import com.afrochow.common.enums.VendorStatus;
import com.afrochow.image.ImageUploadService;
import com.afrochow.image.service.ImageCleanupService;
import com.afrochow.outbox.service.OutboxEventService;
import com.afrochow.user.model.User;
import com.afrochow.user.repository.UserRepository;
import com.afrochow.vendor.VendorMapper;
import com.afrochow.vendor.dto.FoodHandlingCertUploadRequestDto;
import com.afrochow.vendor.dto.VendorProfileResponseDto;
import com.afrochow.vendor.dto.VendorProfileUpdateRequestDto;
import com.afrochow.vendor.model.VendorProfile;
import com.afrochow.vendor.repository.VendorProfileRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VendorProfileServiceTest {

    @Mock private VendorProfileRepository vendorProfileRepository;
    @Mock private UserRepository userRepository;
    @Mock private AddressRepository addressRepository;
    @Mock private ImageUploadService imageUploadService;
    @Mock private ImageCleanupService imageCleanupService;
    @Mock private OutboxEventService outboxEventService;
    @Mock private VendorMapper vendorMapper;

    @InjectMocks private VendorProfileService vendorProfileService;

    private User vendorUser;
    private VendorProfile vendorProfile;
    private Address address;

    @BeforeEach
    void setUp() {
        vendorUser = User.builder().userId(10L).username("jollofhouse")
                .publicUserId("USR10").role(Role.VENDOR).build();
        address = Address.builder().publicAddressId("ADDR1")
                .addressLine("123 Main St").city("Calgary").province(Province.AB)
                .postalCode("T2P1J9").build();
        vendorProfile = VendorProfile.builder()
                .id(5L).user(vendorUser).restaurantName("Jollof House")
                .vendorStatus(VendorStatus.PENDING_PROFILE)
                .address(address)
                .build();
        vendorUser.setVendorProfile(vendorProfile);

        // vendorMapper is fully mocked — its own logic (which touches real
        // VendorProfile helper methods like isOpenNow/hasOperatingDays) is
        // exercised separately and isn't the concern of this service test.
        lenient().when(vendorMapper.toResponseDto(any(VendorProfile.class)))
                .thenAnswer(inv -> {
                    VendorProfile p = inv.getArgument(0);
                    return VendorProfileResponseDto.builder()
                            .restaurantName(p.getRestaurantName())
                            .vendorStatus(p.getVendorStatus())
                            .build();
                });
        lenient().when(vendorMapper.toAddressResponseDto(any(Address.class)))
                .thenAnswer(inv -> {
                    Address a = inv.getArgument(0);
                    return AddressResponseDto.builder()
                            .addressLine(a.getAddressLine()).city(a.getCity()).build();
                });
    }

    private Map<String, VendorProfile.DayHours> oneOpenDay() {
        Map<String, VendorProfile.DayHours> hours = new HashMap<>();
        VendorProfile.DayHours monday = new VendorProfile.DayHours();
        monday.setIsOpen(true);
        monday.setOpenTime("09:00");
        monday.setCloseTime("21:00");
        hours.put("monday", monday);
        return hours;
    }

    // ========== getProfile ==========

    @Test
    void getProfile_returnsMappedDto() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(vendorUser));

        VendorProfileResponseDto result = vendorProfileService.getProfile(10L);

        assertThat(result.getRestaurantName()).isEqualTo("Jollof House");
    }

    @Test
    void getProfile_userNotFound_throwsEntityNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vendorProfileService.getProfile(99L))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void getProfile_userNotVendor_throwsIllegalState() {
        User customer = User.builder().userId(11L).role(Role.CUSTOMER).build();
        when(userRepository.findById(11L)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> vendorProfileService.getProfile(11L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a vendor");
    }

    @Test
    void getProfile_vendorWithNoProfile_throwsEntityNotFound() {
        User vendorWithoutProfile = User.builder().userId(12L).role(Role.VENDOR).build();
        when(userRepository.findById(12L)).thenReturn(Optional.of(vendorWithoutProfile));

        assertThatThrownBy(() -> vendorProfileService.getProfile(12L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Vendor profile not found");
    }

    // ========== updateProfile ==========

    @Test
    void updateProfile_pendingProfile_allowsIdentityAndOperationalEdits() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(vendorUser));
        VendorProfileUpdateRequestDto request = VendorProfileUpdateRequestDto.builder()
                .restaurantName("New Name").description("Tasty food").build();

        VendorProfileResponseDto result = vendorProfileService.updateProfile(10L, request);

        assertThat(vendorProfile.getRestaurantName()).isEqualTo("New Name");
        assertThat(vendorProfile.getDescription()).isEqualTo("Tasty food");
        verify(vendorProfileRepository).save(vendorProfile);
    }

    @Test
    void updateProfile_verifiedVendor_identityFieldIgnoredButOperationalFieldApplied() {
        vendorProfile.setVendorStatus(VendorStatus.VERIFIED);
        vendorProfile.setOperatingHours(oneOpenDay());
        when(userRepository.findById(10L)).thenReturn(Optional.of(vendorUser));
        VendorProfileUpdateRequestDto request = VendorProfileUpdateRequestDto.builder()
                .restaurantName("Attempted Rename").description("Updated description").build();

        vendorProfileService.updateProfile(10L, request);

        assertThat(vendorProfile.getRestaurantName()).isEqualTo("Jollof House"); // unchanged — locked
        assertThat(vendorProfile.getDescription()).isEqualTo("Updated description"); // allowed
    }

    @Test
    void updateProfile_suspendedVendor_throwsIllegalState() {
        vendorProfile.setVendorStatus(VendorStatus.SUSPENDED);
        when(userRepository.findById(10L)).thenReturn(Optional.of(vendorUser));

        assertThatThrownBy(() -> vendorProfileService.updateProfile(10L,
                VendorProfileUpdateRequestDto.builder().description("x").build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be edited");
        verify(vendorProfileRepository, never()).save(any());
    }

    @Test
    void updateProfile_offersDeliveryWithoutFee_throwsIllegalArgument() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(vendorUser));
        VendorProfileUpdateRequestDto request = VendorProfileUpdateRequestDto.builder()
                .offersDelivery(true).build();

        assertThatThrownBy(() -> vendorProfileService.updateProfile(10L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Delivery fee is required");
    }

    @Test
    void updateProfile_offersDeliveryAndPickupBothFalse_throwsIllegalArgument() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(vendorUser));
        VendorProfileUpdateRequestDto request = VendorProfileUpdateRequestDto.builder()
                .offersDelivery(false).offersPickup(false).build();

        assertThatThrownBy(() -> vendorProfileService.updateProfile(10L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least delivery or pickup");
    }

    @Test
    void updateProfile_operatingHoursWithNoOpenDay_throwsIllegalArgument() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(vendorUser));
        Map<String, VendorProfileUpdateRequestDto.OperatingHoursDto> allClosed = new HashMap<>();
        allClosed.put("monday", VendorProfileUpdateRequestDto.OperatingHoursDto.builder()
                .isOpen(false).build());
        VendorProfileUpdateRequestDto request = VendorProfileUpdateRequestDto.builder()
                .operatingHours(allClosed).build();

        assertThatThrownBy(() -> vendorProfileService.updateProfile(10L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("open at least one day");
    }

    @Test
    void updateProfile_pendingProfileBecomesComplete_autoAdvancesToPendingReview() {
        vendorProfile.setStoreCategory("West African");
        vendorProfile.setLogoUrl("https://cdn.example.com/logo.png");
        vendorProfile.setOffersPickup(true);
        vendorProfile.setOperatingHours(oneOpenDay());
        when(userRepository.findById(10L)).thenReturn(Optional.of(vendorUser));
        VendorProfileUpdateRequestDto request = VendorProfileUpdateRequestDto.builder()
                .description("Now complete").build();

        vendorProfileService.updateProfile(10L, request);

        assertThat(vendorProfile.getVendorStatus()).isEqualTo(VendorStatus.PENDING_REVIEW);
        assertThat(vendorProfile.getIsActive()).isTrue();
        assertThat(vendorProfile.getIsVerified()).isFalse();
    }

    @Test
    void updateProfile_pendingProfileStillIncomplete_staysPendingProfile() {
        // Missing logoUrl/offersPickup/operatingHours — not complete yet.
        when(userRepository.findById(10L)).thenReturn(Optional.of(vendorUser));
        VendorProfileUpdateRequestDto request = VendorProfileUpdateRequestDto.builder()
                .description("Still working on it").build();

        vendorProfileService.updateProfile(10L, request);

        assertThat(vendorProfile.getVendorStatus()).isEqualTo(VendorStatus.PENDING_PROFILE);
    }

    // ========== updateAddress ==========

    @Test
    void updateAddress_addressLineChanged_firesGeocodingEvent() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(vendorUser));
        when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));
        AddressRequestDto request = AddressRequestDto.builder()
                .addressLine("456 New St").city("Calgary").province(Province.AB)
                .postalCode("T2P1J9").build();

        vendorProfileService.updateAddress(10L, request);

        verify(outboxEventService).addressGeocodingRequested("ADDR1");
    }

    @Test
    void updateAddress_noGeoRelevantFieldChanged_doesNotFireGeocodingEvent() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(vendorUser));
        when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));
        // Same values as the existing address — nothing geo-relevant actually changes.
        AddressRequestDto request = AddressRequestDto.builder()
                .addressLine("123 Main St").city("Calgary").province(Province.AB)
                .postalCode("T2P1J9").build();

        vendorProfileService.updateAddress(10L, request);

        verify(outboxEventService, never()).addressGeocodingRequested(any());
    }

    @Test
    void updateAddress_vendorHasNoAddress_throwsEntityNotFound() {
        vendorProfile.setAddress(null);
        when(userRepository.findById(10L)).thenReturn(Optional.of(vendorUser));

        assertThatThrownBy(() -> vendorProfileService.updateAddress(10L,
                AddressRequestDto.builder().addressLine("x").city("y")
                        .province(Province.AB).postalCode("T2P1J9").build()))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ========== uploadImage ==========

    @Test
    void uploadImage_logo_replacesAndEnqueuesOldImage() throws Exception {
        vendorProfile.setLogoUrl("https://cdn.example.com/old-logo.png");
        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", "data".getBytes());
        when(userRepository.findByUsername("jollofhouse")).thenReturn(Optional.of(vendorUser));
        // uploadImage looks the user up by username, then getVendorProfileByUserId()
        // looks the same user up again by ID — both calls need stubbing.
        when(userRepository.findById(10L)).thenReturn(Optional.of(vendorUser));
        when(imageUploadService.uploadImageForRegistrationAndGetUrl(file, "vendors/logos"))
                .thenReturn("https://cdn.example.com/new-logo.png");
        when(vendorProfileRepository.save(any(VendorProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        vendorProfileService.uploadImage("jollofhouse", file, "logo");

        assertThat(vendorProfile.getLogoUrl()).isEqualTo("https://cdn.example.com/new-logo.png");
        verify(imageCleanupService).enqueue("https://cdn.example.com/old-logo.png", "vendor-logo-replaced");
    }

    @Test
    void uploadImage_banner_noOldImage_skipsCleanupEnqueue() throws Exception {
        vendorProfile.setBannerUrl(null);
        MockMultipartFile file = new MockMultipartFile("file", "banner.png", "image/png", "data".getBytes());
        when(userRepository.findByUsername("jollofhouse")).thenReturn(Optional.of(vendorUser));
        when(userRepository.findById(10L)).thenReturn(Optional.of(vendorUser));
        when(imageUploadService.uploadImageForRegistrationAndGetUrl(file, "vendors/banners"))
                .thenReturn("https://cdn.example.com/new-banner.png");
        when(vendorProfileRepository.save(any(VendorProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        vendorProfileService.uploadImage("jollofhouse", file, "banner");

        assertThat(vendorProfile.getBannerUrl()).isEqualTo("https://cdn.example.com/new-banner.png");
        verify(imageCleanupService, never()).enqueue(any(), any());
    }

    @Test
    void uploadImage_invalidType_throwsIllegalArgument() {
        MockMultipartFile file = new MockMultipartFile("file", "x.png", "image/png", "data".getBytes());
        when(userRepository.findByUsername("jollofhouse")).thenReturn(Optional.of(vendorUser));
        when(userRepository.findById(10L)).thenReturn(Optional.of(vendorUser));

        assertThatThrownBy(() -> vendorProfileService.uploadImage("jollofhouse", file, "avatar"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid image type");
    }

    @Test
    void uploadImage_blankType_throwsIllegalArgument() {
        MockMultipartFile file = new MockMultipartFile("file", "x.png", "image/png", "data".getBytes());

        assertThatThrownBy(() -> vendorProfileService.uploadImage("jollofhouse", file, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be provided");
    }

    @Test
    void uploadImage_userNotFound_throwsIllegalArgument() {
        MockMultipartFile file = new MockMultipartFile("file", "x.png", "image/png", "data".getBytes());
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vendorProfileService.uploadImage("ghost", file, "logo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");
    }

    // ========== uploadFoodHandlingCert ==========

    @Test
    void uploadFoodHandlingCert_provisionalVendor_savesAndFiresOutboxEvent() throws Exception {
        vendorProfile.setVendorStatus(VendorStatus.PROVISIONAL);
        vendorProfile.setCertVerifiedAt(LocalDateTime.now());
        vendorProfile.setCertVerifiedByAdminId("ADM99");
        MockMultipartFile file = new MockMultipartFile("file", "cert.pdf", "application/pdf", "data".getBytes());
        FoodHandlingCertUploadRequestDto metadata = FoodHandlingCertUploadRequestDto.builder()
                .certNumber("FS-123").issuingBody("FoodSafe BC")
                .certExpiry(LocalDateTime.now().plusYears(2)).build();
        when(userRepository.findById(10L)).thenReturn(Optional.of(vendorUser));
        when(imageUploadService.uploadImageForRegistrationAndGetUrl(file, "vendors/certifications"))
                .thenReturn("https://cdn.example.com/cert.pdf");
        when(vendorProfileRepository.save(any(VendorProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        vendorProfileService.uploadFoodHandlingCert(10L, file, metadata);

        assertThat(vendorProfile.getFoodHandlingCertUrl()).isEqualTo("https://cdn.example.com/cert.pdf");
        assertThat(vendorProfile.getFoodHandlingCertNumber()).isEqualTo("FS-123");
        assertThat(vendorProfile.getCertVerifiedAt()).isNull(); // cleared on re-upload
        assertThat(vendorProfile.getCertVerifiedByAdminId()).isNull();
        verify(outboxEventService).vendorCertificateUploaded(
                vendorProfile.getPublicVendorId(), "USR10", "Jollof House", "https://cdn.example.com/cert.pdf");
    }

    @Test
    void uploadFoodHandlingCert_notProvisional_throwsIllegalState() {
        vendorProfile.setVendorStatus(VendorStatus.PENDING_REVIEW);
        MockMultipartFile file = new MockMultipartFile("file", "cert.pdf", "application/pdf", "data".getBytes());
        when(userRepository.findById(10L)).thenReturn(Optional.of(vendorUser));

        assertThatThrownBy(() -> vendorProfileService.uploadFoodHandlingCert(10L, file,
                FoodHandlingCertUploadRequestDto.builder().certNumber("x").issuingBody("y").build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PROVISIONAL");
    }

    // ========== resubmitForReview ==========

    @Test
    void resubmitForReview_fromRejected_advancesToPendingReview() {
        vendorProfile.setVendorStatus(VendorStatus.REJECTED);
        when(userRepository.findById(10L)).thenReturn(Optional.of(vendorUser));

        vendorProfileService.resubmitForReview(10L);

        assertThat(vendorProfile.getVendorStatus()).isEqualTo(VendorStatus.PENDING_REVIEW);
    }

    @Test
    void resubmitForReview_fromCompletePendingProfile_advancesToPendingReview() {
        vendorProfile.setStoreCategory("West African");
        vendorProfile.setLogoUrl("https://cdn.example.com/logo.png");
        vendorProfile.setOffersDelivery(true);
        vendorProfile.setOperatingHours(oneOpenDay());
        when(userRepository.findById(10L)).thenReturn(Optional.of(vendorUser));

        vendorProfileService.resubmitForReview(10L);

        assertThat(vendorProfile.getVendorStatus()).isEqualTo(VendorStatus.PENDING_REVIEW);
    }

    @Test
    void resubmitForReview_fromIncompletePendingProfile_throwsIllegalState() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(vendorUser));

        assertThatThrownBy(() -> vendorProfileService.resubmitForReview(10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("incomplete");
    }

    @Test
    void resubmitForReview_fromVerified_throwsIllegalState() {
        vendorProfile.setVendorStatus(VendorStatus.VERIFIED);
        when(userRepository.findById(10L)).thenReturn(Optional.of(vendorUser));

        assertThatThrownBy(() -> vendorProfileService.resubmitForReview(10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot submit for review");
    }
}
