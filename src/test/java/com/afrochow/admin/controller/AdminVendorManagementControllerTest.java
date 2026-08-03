package com.afrochow.admin.controller;

import com.afrochow.common.enums.VendorStatus;
import com.afrochow.outbox.service.OutboxEventService;
import com.afrochow.testsupport.AbstractControllerTest;
import com.afrochow.testsupport.ControllerSliceTest;
import com.afrochow.user.model.User;
import com.afrochow.vendor.model.VendorProfile;
import com.afrochow.vendor.repository.VendorProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-layer test for AdminVendorManagementController.
 *
 * Talks to VendorProfileRepository directly (no service layer) plus
 * OutboxEventService for the fire-and-forget notification calls on each
 * status transition. verifyCertAndPromote/verifyVendor take
 * {@code @AuthenticationPrincipal CustomUserDetails}, so those use
 * authenticatedAsPrincipal; every other endpoint takes no auth param.
 * Class-level {@code @PreAuthorize("@deptAccess.can('VENDORS')")} (and the
 * stricter {@code hasRole('SUPERADMIN')} override on stripe-account linking)
 * is not exercised in this slice (see ControllerSliceTest javadoc).
 *
 * linkStripeAccount's happy path calls the real Stripe SDK
 * ({@code Account.retrieve}), which isn't mockable here, so only its
 * pre-Stripe-call validation (invalid account-id format) is covered.
 */
@ControllerSliceTest(AdminVendorManagementController.class)
class AdminVendorManagementControllerTest extends AbstractControllerTest {

    @MockitoBean private VendorProfileRepository vendorProfileRepository;
    @MockitoBean private OutboxEventService outboxEventService;

    private User sampleOwner() {
        return User.builder()
                .publicUserId("vendor-1")
                .email("owner@afrochow.com")
                .firstName("Ada")
                .isActive(true)
                .emailVerified(true)
                .build();
    }

    private VendorProfile sampleVendor(VendorStatus status) {
        return VendorProfile.builder()
                .user(sampleOwner())
                .restaurantName("Mama's Kitchen")
                .storeCategory("Nigerian")
                .vendorStatus(status)
                .isActive(status == VendorStatus.VERIFIED || status == VendorStatus.PROVISIONAL)
                .stripeAccountId("acct_123")
                .stripeOnboardingComplete(true)
                .stripeChargesEnabled(true)
                .stripePayoutsEnabled(true)
                .build();
    }

    @Test
    void getAllVendors_returns200() throws Exception {
        when(vendorProfileRepository.findAll()).thenReturn(List.of(sampleVendor(VendorStatus.VERIFIED)));

        mockMvc.perform(get("/admin/vendors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].publicVendorId").value("vendor-1"));
    }

    @Test
    void getPendingVendors_returns200() throws Exception {
        VendorProfile verifiedOwnerVendor = sampleVendor(VendorStatus.PENDING_REVIEW);
        VendorProfile unverifiedOwnerVendor = sampleVendor(VendorStatus.PENDING_REVIEW);
        unverifiedOwnerVendor.getUser().setEmailVerified(false);

        when(vendorProfileRepository.findByVendorStatus(VendorStatus.PENDING_REVIEW))
                .thenReturn(List.of(verifiedOwnerVendor, unverifiedOwnerVendor));

        mockMvc.perform(get("/admin/vendors/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].publicVendorId").value("vendor-1"));
    }

    @Test
    void getProvisionalVendors_returns200() throws Exception {
        when(vendorProfileRepository.findByVendorStatus(VendorStatus.PROVISIONAL))
                .thenReturn(List.of(sampleVendor(VendorStatus.PROVISIONAL)));

        mockMvc.perform(get("/admin/vendors/provisional"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void getVerifiedVendors_returns200() throws Exception {
        when(vendorProfileRepository.findByVendorStatus(VendorStatus.VERIFIED))
                .thenReturn(List.of(sampleVendor(VendorStatus.VERIFIED)));

        mockMvc.perform(get("/admin/vendors/verified"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void getVendorsByStatus_returns200() throws Exception {
        when(vendorProfileRepository.findByVendorStatus(VendorStatus.SUSPENDED))
                .thenReturn(List.of(sampleVendor(VendorStatus.SUSPENDED)));

        mockMvc.perform(get("/admin/vendors/by-status/{status}", "SUSPENDED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void getVendorsByStatus_invalidStatus_returns400() throws Exception {
        mockMvc.perform(get("/admin/vendors/by-status/{status}", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getVendorDetail_returns200() throws Exception {
        when(vendorProfileRepository.findByPublicVendorId("vendor-1"))
                .thenReturn(Optional.of(sampleVendor(VendorStatus.VERIFIED)));

        mockMvc.perform(get("/admin/vendors/{publicVendorId}", "vendor-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.restaurantName").value("Mama's Kitchen"));
    }

    @Test
    void getVendorDetail_notFound_returns404() throws Exception {
        when(vendorProfileRepository.findByPublicVendorId("ghost")).thenReturn(Optional.empty());

        mockMvc.perform(get("/admin/vendors/{publicVendorId}", "ghost"))
                .andExpect(status().isNotFound());
    }

    @Test
    void approveProvisional_returns200() throws Exception {
        VendorProfile vendor = sampleVendor(VendorStatus.PENDING_REVIEW);
        when(vendorProfileRepository.findByPublicVendorId("vendor-1")).thenReturn(Optional.of(vendor));
        when(vendorProfileRepository.save(any(VendorProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(patch("/admin/vendors/{publicVendorId}/approve-provisional", "vendor-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.vendorStatus").value("PROVISIONAL"));

        verify(outboxEventService).vendorProvisional(eq("vendor-1"), anyString(), anyString(), anyString());
    }

    @Test
    void approveProvisional_wrongStatus_returns400() throws Exception {
        when(vendorProfileRepository.findByPublicVendorId("vendor-1"))
                .thenReturn(Optional.of(sampleVendor(VendorStatus.VERIFIED)));

        mockMvc.perform(patch("/admin/vendors/{publicVendorId}/approve-provisional", "vendor-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verify(vendorProfileRepository, never()).save(any());
    }

    @Test
    void approveProvisional_unverifiedOwnerEmail_returns400() throws Exception {
        VendorProfile vendor = sampleVendor(VendorStatus.PENDING_REVIEW);
        vendor.getUser().setEmailVerified(false);
        when(vendorProfileRepository.findByPublicVendorId("vendor-1")).thenReturn(Optional.of(vendor));

        mockMvc.perform(patch("/admin/vendors/{publicVendorId}/approve-provisional", "vendor-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Vendor owner must verify their email before admin approval."));

        verify(vendorProfileRepository, never()).save(any());
    }

    @Test
    void approveProvisional_stripeNotReady_returns400() throws Exception {
        VendorProfile vendor = sampleVendor(VendorStatus.PENDING_REVIEW);
        vendor.setStripeAccountId(null);
        when(vendorProfileRepository.findByPublicVendorId("vendor-1")).thenReturn(Optional.of(vendor));

        mockMvc.perform(patch("/admin/vendors/{publicVendorId}/approve-provisional", "vendor-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Vendor must complete Stripe Connect onboarding before admin approval."));

        verify(vendorProfileRepository, never()).save(any());
    }

    @Test
    void verifyCertAndPromote_returns200() throws Exception {
        VendorProfile vendor = sampleVendor(VendorStatus.PROVISIONAL);
        vendor.setFoodHandlingCertUrl("https://certs.afrochow.com/cert.pdf");
        vendor.setFoodHandlingCertExpiry(LocalDateTime.now().plusYears(1));
        when(vendorProfileRepository.findByPublicVendorId("vendor-1")).thenReturn(Optional.of(vendor));
        when(vendorProfileRepository.save(any(VendorProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(patch("/admin/vendors/{publicVendorId}/verify-cert", "vendor-1")
                        .with(authenticatedAsPrincipal(User.builder().publicUserId("admin-1").build(), "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.vendorStatus").value("VERIFIED"));

        verify(outboxEventService).vendorApproved(eq("vendor-1"), anyString(), anyString(), anyString());
    }

    @Test
    void verifyCertAndPromote_noCert_returns400() throws Exception {
        VendorProfile vendor = sampleVendor(VendorStatus.PROVISIONAL);
        when(vendorProfileRepository.findByPublicVendorId("vendor-1")).thenReturn(Optional.of(vendor));

        mockMvc.perform(patch("/admin/vendors/{publicVendorId}/verify-cert", "vendor-1")
                        .with(authenticatedAsPrincipal(User.builder().publicUserId("admin-1").build(), "ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verify(vendorProfileRepository, never()).save(any());
    }

    @Test
    void verifyVendor_bypassCert_returns200() throws Exception {
        VendorProfile vendor = sampleVendor(VendorStatus.PENDING_REVIEW);
        when(vendorProfileRepository.findByPublicVendorId("vendor-1")).thenReturn(Optional.of(vendor));
        when(vendorProfileRepository.save(any(VendorProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(patch("/admin/vendors/{publicVendorId}/verify", "vendor-1")
                        .with(authenticatedAsPrincipal(User.builder().publicUserId("admin-1").build(), "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.vendorStatus").value("VERIFIED"));
    }

    @Test
    void verifyVendor_unverifiedOwnerEmail_returns400() throws Exception {
        VendorProfile vendor = sampleVendor(VendorStatus.PENDING_REVIEW);
        vendor.getUser().setEmailVerified(false);
        when(vendorProfileRepository.findByPublicVendorId("vendor-1")).thenReturn(Optional.of(vendor));

        mockMvc.perform(patch("/admin/vendors/{publicVendorId}/verify", "vendor-1")
                        .with(authenticatedAsPrincipal(User.builder().publicUserId("admin-1").build(), "ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Vendor owner must verify their email before admin approval."));

        verify(vendorProfileRepository, never()).save(any());
    }

    @Test
    void suspendVendor_returns200() throws Exception {
        VendorProfile vendor = sampleVendor(VendorStatus.VERIFIED);
        when(vendorProfileRepository.findByPublicVendorId("vendor-1")).thenReturn(Optional.of(vendor));
        when(vendorProfileRepository.save(any(VendorProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(patch("/admin/vendors/{publicVendorId}/suspend", "vendor-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.vendorStatus").value("SUSPENDED"));
    }

    @Test
    void suspendVendor_wrongStatus_returns400() throws Exception {
        when(vendorProfileRepository.findByPublicVendorId("vendor-1"))
                .thenReturn(Optional.of(sampleVendor(VendorStatus.PENDING_REVIEW)));

        mockMvc.perform(patch("/admin/vendors/{publicVendorId}/suspend", "vendor-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void reinstateVendor_returns200() throws Exception {
        VendorProfile vendor = sampleVendor(VendorStatus.SUSPENDED);
        when(vendorProfileRepository.findByPublicVendorId("vendor-1")).thenReturn(Optional.of(vendor));
        when(vendorProfileRepository.save(any(VendorProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(patch("/admin/vendors/{publicVendorId}/reinstate", "vendor-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.vendorStatus").value("VERIFIED"));
    }

    @Test
    void reinstateVendor_wrongStatus_returns400() throws Exception {
        when(vendorProfileRepository.findByPublicVendorId("vendor-1"))
                .thenReturn(Optional.of(sampleVendor(VendorStatus.VERIFIED)));

        mockMvc.perform(patch("/admin/vendors/{publicVendorId}/reinstate", "vendor-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void rejectVendor_returns200() throws Exception {
        VendorProfile vendor = sampleVendor(VendorStatus.PENDING_REVIEW);
        when(vendorProfileRepository.findByPublicVendorId("vendor-1")).thenReturn(Optional.of(vendor));
        when(vendorProfileRepository.save(any(VendorProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminVendorManagementController.RejectRequestDto body = new AdminVendorManagementController.RejectRequestDto();
        body.setReason("Incomplete documentation");

        mockMvc.perform(post("/admin/vendors/{publicVendorId}/reject", "vendor-1")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.vendorStatus").value("REJECTED"));

        verify(outboxEventService).vendorRejected(eq("vendor-1"), anyString(), anyString(), anyString(), eq("Incomplete documentation"));
    }

    @Test
    void rejectVendor_verifiedVendor_returns400() throws Exception {
        when(vendorProfileRepository.findByPublicVendorId("vendor-1"))
                .thenReturn(Optional.of(sampleVendor(VendorStatus.VERIFIED)));

        mockMvc.perform(post("/admin/vendors/{publicVendorId}/reject", "vendor-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verify(vendorProfileRepository, never()).save(any());
    }

    @Test
    void linkStripeAccount_invalidFormat_returns400() throws Exception {
        AdminVendorManagementController.LinkStripeAccountDto body = new AdminVendorManagementController.LinkStripeAccountDto();
        body.setStripeAccountId("not-a-stripe-id");

        mockMvc.perform(patch("/admin/vendors/{publicVendorId}/stripe-account", "vendor-1")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verify(vendorProfileRepository, never()).findByPublicVendorId(any());
    }
}
