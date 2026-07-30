package com.afrochow.vendor.controller;

import com.afrochow.testsupport.AbstractControllerTest;
import com.afrochow.testsupport.ControllerSliceTest;
import com.afrochow.user.model.User;
import com.afrochow.vendor.dto.VendorProfileResponseDto;
import com.afrochow.vendor.service.StripeConnectService;
import com.afrochow.vendor.service.VendorProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-layer test for VendorStripeController.
 *
 * Every endpoint takes {@code @AuthenticationPrincipal CustomUserDetails}
 * and reads {@code getUserId()} — authenticatedAsPrincipal with a User that
 * has userId populated. No class-level @PreAuthorize here (access control is
 * via the security filter chain, hasRole('VENDOR'), not exercised in a
 * @WebMvcTest slice).
 */
@ControllerSliceTest(VendorStripeController.class)
class VendorStripeControllerTest extends AbstractControllerTest {

    @MockitoBean private StripeConnectService stripeConnectService;
    @MockitoBean private VendorProfileService vendorProfileService;

    private static final Long USER_ID = 1L;

    private User vendorUser() {
        return User.builder().userId(USER_ID).publicUserId("vendor-1").build();
    }

    @Test
    void startOnboarding_returns200() throws Exception {
        when(stripeConnectService.createConnectAccountAndGetOnboardingUrl(USER_ID))
                .thenReturn("https://connect.stripe.com/setup/onboarding-url");

        mockMvc.perform(post("/vendor/stripe/connect").with(authenticatedAsPrincipal(vendorUser(), "VENDOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onboardingUrl").value("https://connect.stripe.com/setup/onboarding-url"));
    }

    @Test
    void getStatus_returns200() throws Exception {
        VendorProfileResponseDto profile = VendorProfileResponseDto.builder()
                .publicUserId("vendor-1")
                .stripeAccountId("acct_test123")
                .stripeOnboardingComplete(true)
                .build();
        when(vendorProfileService.getProfile(USER_ID)).thenReturn(profile);

        mockMvc.perform(get("/vendor/stripe/connect/status").with(authenticatedAsPrincipal(vendorUser(), "VENDOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stripeAccountId").value("acct_test123"))
                .andExpect(jsonPath("$.data.stripeOnboardingComplete").value(true));
    }

    @Test
    void refreshOnboardingLink_returns200() throws Exception {
        when(stripeConnectService.createConnectAccountAndGetOnboardingUrl(USER_ID))
                .thenReturn("https://connect.stripe.com/setup/refreshed-url");

        mockMvc.perform(get("/vendor/stripe/connect/onboarding-link").with(authenticatedAsPrincipal(vendorUser(), "VENDOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onboardingUrl").value("https://connect.stripe.com/setup/refreshed-url"));
    }

    @Test
    void getDashboardLink_returns200() throws Exception {
        when(stripeConnectService.generateDashboardLink(USER_ID))
                .thenReturn("https://connect.stripe.com/express/dashboard-url");

        mockMvc.perform(get("/vendor/stripe/connect/dashboard").with(authenticatedAsPrincipal(vendorUser(), "VENDOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dashboardUrl").value("https://connect.stripe.com/express/dashboard-url"));
    }
}
