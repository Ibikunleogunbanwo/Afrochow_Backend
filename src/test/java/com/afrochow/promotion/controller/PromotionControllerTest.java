package com.afrochow.promotion.controller;

import com.afrochow.common.enums.PromotionType;
import com.afrochow.promotion.dto.PromotionPreviewRequestDto;
import com.afrochow.promotion.dto.PromotionPreviewResponseDto;
import com.afrochow.promotion.dto.PromotionRequestDto;
import com.afrochow.promotion.dto.PromotionResponseDto;
import com.afrochow.promotion.service.PromotionService;
import com.afrochow.testsupport.AbstractControllerTest;
import com.afrochow.testsupport.ControllerSliceTest;
import com.afrochow.user.model.User;
import com.afrochow.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-layer test for PromotionController.
 *
 * See CategoryControllerTest / AbstractControllerTest / ControllerSliceTest
 * for the shared @WebMvcTest slice setup. PromotionController also
 * constructor-injects UserRepository directly (used in previewDiscount to
 * resolve a public user ID) — @WebMvcTest doesn't auto-configure JPA
 * repositories, so that has to be mocked here too, same as the service.
 * @PreAuthorize/@deptAccess enforcement on vendor/admin endpoints is not
 * exercised in this slice (see ControllerSliceTest javadoc); those tests
 * only cover routing, @Valid validation, and response shape.
 */
@ControllerSliceTest(PromotionController.class)
class PromotionControllerTest extends AbstractControllerTest {

    @MockitoBean private PromotionService promotionService;
    @MockitoBean private UserRepository userRepository;

    private static final String VENDOR_USERNAME = "vendor-user";

    private PromotionResponseDto samplePromotion(String publicId, String code) {
        return PromotionResponseDto.builder()
                .publicPromotionId(publicId)
                .code(code)
                .title("10% off")
                .type(PromotionType.PERCENTAGE)
                .value(BigDecimal.TEN)
                .isActive(true)
                .isCurrentlyActive(true)
                .build();
    }

    private PromotionRequestDto validRequest() {
        return PromotionRequestDto.builder()
                .code("SAVE10")
                .title("Save 10%")
                .type(PromotionType.PERCENTAGE)
                .value(BigDecimal.TEN)
                .startDate("2026-01-01T00:00:00")
                .endDate("2026-12-31T23:59:59")
                .build();
    }

    // ========== CUSTOMER / PUBLIC ENDPOINTS ==========

    @Test
    void getActivePromotions_returns200() throws Exception {
        when(promotionService.getActivePromotions())
                .thenReturn(List.of(samplePromotion("promo-1", "SAVE10")));

        mockMvc.perform(get("/promotions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].code").value("SAVE10"));
    }

    @Test
    void getActivePromotionsForVendor_returns200() throws Exception {
        when(promotionService.getActivePromotionsForVendor("vendor-1"))
                .thenReturn(List.of(samplePromotion("promo-1", "SAVE10")));

        mockMvc.perform(get("/promotions/vendor/{vendorPublicId}", "vendor-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("SAVE10"));
    }

    @Test
    void validateCode_returns200() throws Exception {
        when(promotionService.validateCode("SAVE10", null))
                .thenReturn(samplePromotion("promo-1", "SAVE10"));

        mockMvc.perform(get("/promotions/validate/{code}", "SAVE10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("SAVE10"));
    }

    @Test
    void previewDiscount_returns200() throws Exception {
        PromotionPreviewRequestDto request =
                new PromotionPreviewRequestDto("SAVE10", "vendor-1", new BigDecimal("50.00"), new BigDecimal("5.00"));
        when(userRepository.findByUsername(VENDOR_USERNAME))
                .thenReturn(Optional.of(User.builder().publicUserId("user-1").build()));
        when(promotionService.previewDiscount(any(PromotionPreviewRequestDto.class), eq("user-1")))
                .thenReturn(PromotionPreviewResponseDto.builder()
                        .promoCode("SAVE10")
                        .discountAmount(new BigDecimal("5.00"))
                        .discountedSubtotal(new BigDecimal("45.00"))
                        .build());

        mockMvc.perform(post("/promotions/preview")
                        .with(authenticatedAs(VENDOR_USERNAME, "CUSTOMER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.discountAmount").value(5.00));
    }

    // ========== VENDOR ENDPOINTS (security not exercised in this slice) ==========

    @Test
    void getMyPromotions_returns200() throws Exception {
        when(promotionService.getVendorOwnPromotions(VENDOR_USERNAME))
                .thenReturn(List.of(samplePromotion("promo-1", "SAVE10")));

        mockMvc.perform(get("/promotions/vendor/mine").with(authenticatedAs(VENDOR_USERNAME, "VENDOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("SAVE10"));
    }

    @Test
    void createVendorPromotion_valid_returns201() throws Exception {
        when(promotionService.createVendorPromotion(eq(VENDOR_USERNAME), any(PromotionRequestDto.class)))
                .thenReturn(samplePromotion("promo-1", "SAVE10"));

        mockMvc.perform(post("/promotions/vendor")
                        .with(authenticatedAs(VENDOR_USERNAME, "VENDOR"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("SAVE10"));
    }

    @Test
    void createVendorPromotion_blankCode_returns400WithValidationErrors() throws Exception {
        PromotionRequestDto request = validRequest();
        request.setCode("");

        mockMvc.perform(post("/promotions/vendor")
                        .with(authenticatedAs(VENDOR_USERNAME, "VENDOR"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verify(promotionService, never()).createVendorPromotion(any(), any());
    }

    @Test
    void updateVendorPromotion_valid_returns200() throws Exception {
        when(promotionService.updateVendorPromotion(eq(VENDOR_USERNAME), eq("promo-1"), any(PromotionRequestDto.class)))
                .thenReturn(samplePromotion("promo-1", "SAVE20"));

        mockMvc.perform(put("/promotions/vendor/{publicPromotionId}", "promo-1")
                        .with(authenticatedAs(VENDOR_USERNAME, "VENDOR"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("SAVE20"));
    }

    @Test
    void deactivateVendorPromotion_returns200() throws Exception {
        doNothing().when(promotionService).deactivateVendorPromotion(VENDOR_USERNAME, "promo-1");

        mockMvc.perform(delete("/promotions/vendor/{publicPromotionId}", "promo-1")
                        .with(authenticatedAs(VENDOR_USERNAME, "VENDOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ========== ADMIN ENDPOINTS (security not exercised in this slice) ==========

    @Test
    void getAllPromotions_returns200() throws Exception {
        when(promotionService.getAllPromotions())
                .thenReturn(List.of(samplePromotion("promo-1", "SAVE10"), samplePromotion("promo-2", "SAVE20")));

        mockMvc.perform(get("/promotions/admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void getPromotion_returns200() throws Exception {
        when(promotionService.getPromotionByPublicId("promo-1"))
                .thenReturn(samplePromotion("promo-1", "SAVE10"));

        mockMvc.perform(get("/promotions/admin/{publicPromotionId}", "promo-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("SAVE10"));
    }

    @Test
    void createPromotion_valid_returns201() throws Exception {
        when(promotionService.createPromotion(any(PromotionRequestDto.class)))
                .thenReturn(samplePromotion("promo-1", "SAVE10"));

        mockMvc.perform(post("/promotions/admin")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("SAVE10"));
    }

    @Test
    void updatePromotion_valid_returns200() throws Exception {
        when(promotionService.updatePromotion(eq("promo-1"), any(PromotionRequestDto.class)))
                .thenReturn(samplePromotion("promo-1", "SAVE20"));

        mockMvc.perform(put("/promotions/admin/{publicPromotionId}", "promo-1")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("SAVE20"));
    }

    @Test
    void deactivatePromotion_returns200() throws Exception {
        doNothing().when(promotionService).deactivatePromotion("promo-1");

        mockMvc.perform(delete("/promotions/admin/{publicPromotionId}", "promo-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void activatePromotion_returns200() throws Exception {
        when(promotionService.activatePromotion("promo-1"))
                .thenReturn(samplePromotion("promo-1", "SAVE10"));

        mockMvc.perform(patch("/promotions/admin/{publicPromotionId}/activate", "promo-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isActive").value(true));
    }

    @Test
    void deletePromotion_returns200() throws Exception {
        doNothing().when(promotionService).deletePromotion("promo-1");

        mockMvc.perform(delete("/promotions/admin/{publicPromotionId}/permanent", "promo-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
