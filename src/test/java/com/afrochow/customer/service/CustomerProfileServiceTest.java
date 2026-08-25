package com.afrochow.customer.service;

import com.afrochow.address.mapper.AddressMapper;
import com.afrochow.address.dto.AddressRequestDto;
import com.afrochow.common.enums.PaymentMethod;
import com.afrochow.common.enums.Province;
import com.afrochow.common.enums.Role;
import com.afrochow.common.exceptions.PasswordPolicyViolationException;
import com.afrochow.customer.dto.CompleteProfileRequestDto;
import com.afrochow.customer.dto.CustomerPasswordUpdateDto;
import com.afrochow.customer.dto.CustomerProfileResponseDto;
import com.afrochow.customer.dto.CustomerUpdateRequestDto;
import com.afrochow.customer.model.CustomerProfile;
import com.afrochow.customer.repository.CustomerProfileRepository;
import com.afrochow.image.service.ImageUploadService;
import com.afrochow.image.service.ImageCleanupService;
import com.afrochow.security.service.PasswordPolicyService;
import com.afrochow.security.model.CustomUserDetails;
import com.afrochow.user.model.User;
import com.afrochow.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerProfileServiceTest {

    @Mock private CustomerProfileRepository customerProfileRepository;
    @Mock private AddressMapper addressMapper;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private ImageUploadService imageUploadService;
    @Mock private ImageCleanupService imageCleanupService;
    @Mock private PasswordPolicyService passwordPolicyService;

    @InjectMocks private CustomerProfileService customerProfileService;

    private User customer;
    private CustomerProfile profile;

    @BeforeEach
    void setUp() {
        customer = User.builder().userId(1L).publicUserId("CUS1").username("adecustomer")
                .email("customer@example.com").firstName("Ade").lastName("Customer")
                .phone("4165551234").password("encoded-old-pass").role(Role.CUSTOMER).build();
        profile = CustomerProfile.builder().customerProfileId(1L).user(customer)
                .loyaltyPoints(100).notificationsEnabled(true).build();
        customer.setCustomerProfile(profile);
    }

    // ========== getProfile ==========

    @Test
    void getProfile_customer_returnsMappedDto() {
        when(userRepository.findByPublicUserId("CUS1")).thenReturn(Optional.of(customer));

        CustomerProfileResponseDto result = customerProfileService.getProfile("CUS1");

        assertThat(result.getPublicUserId()).isEqualTo("CUS1");
        assertThat(result.getLoyaltyPoints()).isEqualTo(100);
        assertThat(result.getIsProfileComplete()).isTrue();
    }

    @Test
    void getProfile_userNotFound_throwsEntityNotFound() {
        when(userRepository.findByPublicUserId("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerProfileService.getProfile("ghost"))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void getProfile_nonCustomer_throwsIllegalState() {
        User vendor = User.builder().userId(2L).publicUserId("VEN1").role(Role.VENDOR).build();
        when(userRepository.findByPublicUserId("VEN1")).thenReturn(Optional.of(vendor));

        assertThatThrownBy(() -> customerProfileService.getProfile("VEN1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a customer");
    }

    @Test
    void getProfile_noProfile_throwsEntityNotFound() {
        User customerNoProfile = User.builder().userId(3L).publicUserId("CUS2").role(Role.CUSTOMER).build();
        when(userRepository.findByPublicUserId("CUS2")).thenReturn(Optional.of(customerNoProfile));

        assertThatThrownBy(() -> customerProfileService.getProfile("CUS2"))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ========== updateProfile ==========

    @Test
    void updateProfile_updatesNameAndDeliveryInstructions() {
        when(userRepository.findByPublicUserId("CUS1")).thenReturn(Optional.of(customer));
        CustomerUpdateRequestDto request = CustomerUpdateRequestDto.builder()
                .firstName("Adebayo").defaultDeliveryInstructions("Leave at door").build();

        customerProfileService.updateProfile("CUS1", request);

        assertThat(customer.getFirstName()).isEqualTo("Adebayo");
        assertThat(profile.getDefaultDeliveryInstructions()).isEqualTo("Leave at door");
        verify(userRepository).save(customer);
        verify(customerProfileRepository).save(profile);
    }

    @Test
    void updateProfile_samePhoneResubmitted_doesNotThrow() {
        when(userRepository.findByPublicUserId("CUS1")).thenReturn(Optional.of(customer));
        CustomerUpdateRequestDto request = CustomerUpdateRequestDto.builder().phone("4165551234").build();

        customerProfileService.updateProfile("CUS1", request);

        verify(userRepository, never()).findByPhone(any());
        assertThat(customer.getPhone()).isEqualTo("4165551234");
    }

    @Test
    void updateProfile_newPhoneAlreadyUsedByAnotherUser_throwsIllegalState() {
        when(userRepository.findByPublicUserId("CUS1")).thenReturn(Optional.of(customer));
        User otherUser = User.builder().userId(9L).publicUserId("CUS9").phone("6475559999").build();
        when(userRepository.findByPhone("6475559999")).thenReturn(Optional.of(otherUser));
        CustomerUpdateRequestDto request = CustomerUpdateRequestDto.builder().phone("6475559999").build();

        assertThatThrownBy(() -> customerProfileService.updateProfile("CUS1", request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already in use");
    }

    @Test
    void updateProfile_newPhoneUnused_updatesPhone() {
        when(userRepository.findByPublicUserId("CUS1")).thenReturn(Optional.of(customer));
        when(userRepository.findByPhone("6475559999")).thenReturn(Optional.empty());
        CustomerUpdateRequestDto request = CustomerUpdateRequestDto.builder().phone("6475559999").build();

        customerProfileService.updateProfile("CUS1", request);

        assertThat(customer.getPhone()).isEqualTo("6475559999");
    }

    @Test
    void updateProfile_blankProfileImageUrl_ignoredNotSet() {
        customer.setProfileImageUrl("https://old-image.jpg");
        when(userRepository.findByPublicUserId("CUS1")).thenReturn(Optional.of(customer));
        CustomerUpdateRequestDto request = CustomerUpdateRequestDto.builder().profileImageUrl("  ").build();

        customerProfileService.updateProfile("CUS1", request);

        assertThat(customer.getProfileImageUrl()).isEqualTo("https://old-image.jpg");
    }

    @Test
    void updateProfile_paymentMethodUpdated() {
        when(userRepository.findByPublicUserId("CUS1")).thenReturn(Optional.of(customer));
        CustomerUpdateRequestDto request = CustomerUpdateRequestDto.builder()
                .paymentMethod(PaymentMethod.PAYPAL).build();

        customerProfileService.updateProfile("CUS1", request);

        assertThat(profile.getPaymentMethod()).isEqualTo(PaymentMethod.PAYPAL);
    }

    // ========== updateNotificationPreference ==========

    @Test
    void updateNotificationPreference_updatesFlag() {
        when(userRepository.findByPublicUserId("CUS1")).thenReturn(Optional.of(customer));

        customerProfileService.updateNotificationPreference("CUS1", false);

        assertThat(profile.getNotificationsEnabled()).isFalse();
        verify(customerProfileRepository).save(profile);
    }

    // ========== updatePassword ==========

    @Test
    void updatePassword_success_encodesAndSaves() {
        when(userRepository.findByPublicUserId("CUS1")).thenReturn(Optional.of(customer));
        when(passwordEncoder.matches("OldPass1!", "encoded-old-pass")).thenReturn(true);
        when(passwordEncoder.matches("NewPass1!", "encoded-old-pass")).thenReturn(false);
        when(passwordEncoder.encode("NewPass1!")).thenReturn("encoded-new-pass");
        CustomerPasswordUpdateDto dto = CustomerPasswordUpdateDto.builder()
                .oldPassword("OldPass1!").newPassword("NewPass1!").confirmNewPassword("NewPass1!").build();

        customerProfileService.updatePassword("CUS1", dto);

        assertThat(customer.getPassword()).isEqualTo("encoded-new-pass");
        verify(passwordPolicyService).validatePassword("NewPass1!");
        verify(userRepository).save(customer);
    }

    @Test
    void updatePassword_wrongOldPassword_throwsIllegalArgument() {
        when(userRepository.findByPublicUserId("CUS1")).thenReturn(Optional.of(customer));
        when(passwordEncoder.matches("WrongPass", "encoded-old-pass")).thenReturn(false);
        CustomerPasswordUpdateDto dto = CustomerPasswordUpdateDto.builder()
                .oldPassword("WrongPass").newPassword("NewPass1!").confirmNewPassword("NewPass1!").build();

        assertThatThrownBy(() -> customerProfileService.updatePassword("CUS1", dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("incorrect");
    }

    @Test
    void updatePassword_mismatchedConfirmation_throwsIllegalArgument() {
        when(userRepository.findByPublicUserId("CUS1")).thenReturn(Optional.of(customer));
        when(passwordEncoder.matches("OldPass1!", "encoded-old-pass")).thenReturn(true);
        CustomerPasswordUpdateDto dto = CustomerPasswordUpdateDto.builder()
                .oldPassword("OldPass1!").newPassword("NewPass1!").confirmNewPassword("Different1!").build();

        assertThatThrownBy(() -> customerProfileService.updatePassword("CUS1", dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("do not match");
    }

    @Test
    void updatePassword_violatesPolicy_throwsPasswordPolicyViolation() {
        when(userRepository.findByPublicUserId("CUS1")).thenReturn(Optional.of(customer));
        when(passwordEncoder.matches("OldPass1!", "encoded-old-pass")).thenReturn(true);
        doThrow(new PasswordPolicyViolationException(List.of("too weak")))
                .when(passwordPolicyService).validatePassword("weak");
        CustomerPasswordUpdateDto dto = CustomerPasswordUpdateDto.builder()
                .oldPassword("OldPass1!").newPassword("weak").confirmNewPassword("weak").build();

        assertThatThrownBy(() -> customerProfileService.updatePassword("CUS1", dto))
                .isInstanceOf(PasswordPolicyViolationException.class);
    }

    @Test
    void updatePassword_sameAsCurrentPassword_throwsIllegalArgument() {
        when(userRepository.findByPublicUserId("CUS1")).thenReturn(Optional.of(customer));
        when(passwordEncoder.matches("OldPass1!", "encoded-old-pass")).thenReturn(true);
        CustomerPasswordUpdateDto dto = CustomerPasswordUpdateDto.builder()
                .oldPassword("OldPass1!").newPassword("OldPass1!").confirmNewPassword("OldPass1!").build();

        assertThatThrownBy(() -> customerProfileService.updatePassword("CUS1", dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be different");
    }

    // ========== uploadProfileImage ==========

    @Test
    void uploadProfileImage_selfHostedOldImage_enqueuesCleanup() throws Exception {
        customer.setProfileImageUrl("customer/profile_image/old.jpg");
        CustomUserDetails userDetails = new CustomUserDetails(customer, List.of());
        MockMultipartFile file = new MockMultipartFile("file", "new.jpg", "image/jpeg", "data".getBytes());
        when(userRepository.findByPublicUserId("CUS1")).thenReturn(Optional.of(customer));
        when(imageUploadService.uploadImageForRegistrationAndGetUrl(file, "customer/profile_image"))
                .thenReturn("customer/profile_image/new.jpg");

        customerProfileService.uploadProfileImage(file, userDetails);

        assertThat(customer.getProfileImageUrl()).isEqualTo("customer/profile_image/new.jpg");
        verify(imageCleanupService).enqueue("customer/profile_image/old.jpg", "customer-profile-image-replaced");
    }

    @Test
    void uploadProfileImage_googleHostedOldImage_skipsCleanup() throws Exception {
        customer.setProfileImageUrl("https://lh3.googleusercontent.com/avatar.jpg");
        CustomUserDetails userDetails = new CustomUserDetails(customer, List.of());
        MockMultipartFile file = new MockMultipartFile("file", "new.jpg", "image/jpeg", "data".getBytes());
        when(userRepository.findByPublicUserId("CUS1")).thenReturn(Optional.of(customer));
        when(imageUploadService.uploadImageForRegistrationAndGetUrl(file, "customer/profile_image"))
                .thenReturn("customer/profile_image/new.jpg");

        customerProfileService.uploadProfileImage(file, userDetails);

        verify(imageCleanupService, never()).enqueue(any(), any());
    }

    // ========== completeProfile ==========

    @Test
    void completeProfile_setsPhoneAndOptionalFields() {
        User googleUser = User.builder().userId(5L).publicUserId("CUS5").username("googleuser")
                .role(Role.CUSTOMER).build();
        CustomerProfile googleProfile = CustomerProfile.builder().customerProfileId(5L).user(googleUser).build();
        googleUser.setCustomerProfile(googleProfile);
        when(userRepository.findByPublicUserId("CUS5")).thenReturn(Optional.of(googleUser));
        when(userRepository.existsByPhone("4165551234")).thenReturn(false);
        CompleteProfileRequestDto dto = CompleteProfileRequestDto.builder()
                .phone("416-555-1234").build();

        customerProfileService.completeProfile("CUS5", dto);

        assertThat(googleUser.getPhone()).isEqualTo("4165551234");
        verify(userRepository).save(googleUser);
    }

    @Test
    void completeProfile_phoneAlreadyRegisteredToAnotherAccount_throwsIllegalArgument() {
        User googleUser = User.builder().userId(5L).publicUserId("CUS5").role(Role.CUSTOMER).build();
        CustomerProfile googleProfile = CustomerProfile.builder().user(googleUser).build();
        googleUser.setCustomerProfile(googleProfile);
        when(userRepository.findByPublicUserId("CUS5")).thenReturn(Optional.of(googleUser));
        when(userRepository.existsByPhone("4165551234")).thenReturn(true);
        CompleteProfileRequestDto dto = CompleteProfileRequestDto.builder().phone("4165551234").build();

        assertThatThrownBy(() -> customerProfileService.completeProfile("CUS5", dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    void completeProfile_withAddress_addsAddressToProfile() {
        User googleUser = User.builder().userId(5L).publicUserId("CUS5").role(Role.CUSTOMER).build();
        CustomerProfile googleProfile = CustomerProfile.builder().user(googleUser).build();
        googleUser.setCustomerProfile(googleProfile);
        when(userRepository.findByPublicUserId("CUS5")).thenReturn(Optional.of(googleUser));
        when(userRepository.existsByPhone("4165551234")).thenReturn(false);
        AddressRequestDto addressDto = AddressRequestDto.builder()
                .addressLine("1 Main St").city("Calgary").province(Province.AB).postalCode("T2P1J9").build();
        CompleteProfileRequestDto dto = CompleteProfileRequestDto.builder()
                .phone("4165551234").address(addressDto).build();

        customerProfileService.completeProfile("CUS5", dto);

        assertThat(googleProfile.getAddresses()).hasSize(1);
        assertThat(googleProfile.getAddresses().get(0).getCity()).isEqualTo("Calgary");
    }

    @Test
    void completeProfile_usernameAlreadyTaken_throwsIllegalArgument() {
        User googleUser = User.builder().userId(5L).publicUserId("CUS5").username("googleuser").role(Role.CUSTOMER).build();
        CustomerProfile googleProfile = CustomerProfile.builder().user(googleUser).build();
        googleUser.setCustomerProfile(googleProfile);
        when(userRepository.findByPublicUserId("CUS5")).thenReturn(Optional.of(googleUser));
        when(userRepository.existsByPhone("4165551234")).thenReturn(false);
        when(userRepository.existsByUsername("takenname")).thenReturn(true);
        CompleteProfileRequestDto dto = CompleteProfileRequestDto.builder()
                .phone("4165551234").username("takenname").build();

        assertThatThrownBy(() -> customerProfileService.completeProfile("CUS5", dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already taken");
    }

    @Test
    void completeProfile_nonCustomer_throwsIllegalState() {
        User vendor = User.builder().userId(6L).publicUserId("VEN1").role(Role.VENDOR).build();
        when(userRepository.findByPublicUserId("VEN1")).thenReturn(Optional.of(vendor));

        assertThatThrownBy(() -> customerProfileService.completeProfile("VEN1",
                CompleteProfileRequestDto.builder().phone("4165551234").build()))
                .isInstanceOf(IllegalStateException.class);
    }
}
