package com.afrochow.waitlist.controller;

import com.afrochow.testsupport.AbstractControllerTest;
import com.afrochow.testsupport.ControllerSliceTest;
import com.afrochow.waitlist.dto.WaitlistRequestDto;
import com.afrochow.waitlist.dto.WaitlistResponseDto;
import com.afrochow.waitlist.service.WaitlistService;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-layer test for WaitlistController.
 *
 * See CategoryControllerTest / AbstractControllerTest / ControllerSliceTest
 * for the shared @WebMvcTest slice setup. This is a single-endpoint, fully
 * public controller (no Authentication param), so it only needs the
 * request-routing / @Valid / response-shape coverage.
 */
@ControllerSliceTest(WaitlistController.class)
class WaitlistControllerTest extends AbstractControllerTest {

    @MockitoBean private WaitlistService waitlistService;

    private WaitlistRequestDto validRequest() {
        WaitlistRequestDto request = new WaitlistRequestDto();
        request.setName("Ada Lovelace");
        request.setEmail("ada@example.com");
        request.setCity("Calgary");
        request.setRole("customer");
        return request;
    }

    @Test
    void joinWaitlist_valid_returns201() throws Exception {
        WaitlistResponseDto response = WaitlistResponseDto.builder()
                .publicWaitlistId("wl-1")
                .name("Ada Lovelace")
                .email("ada@example.com")
                .city("Calgary")
                .role("customer")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        when(waitlistService.join(any(WaitlistRequestDto.class))).thenReturn(response);

        mockMvc.perform(post("/waitlist")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Waitlist entry saved"))
                .andExpect(jsonPath("$.data.publicWaitlistId").value("wl-1"))
                .andExpect(jsonPath("$.data.email").value("ada@example.com"));
    }

    @Test
    void joinWaitlist_blankName_returns400WithValidationErrors() throws Exception {
        WaitlistRequestDto request = validRequest();
        request.setName("");

        mockMvc.perform(post("/waitlist")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data[0].field").value("name"));
    }

    @Test
    void joinWaitlist_invalidEmail_returns400WithValidationErrors() throws Exception {
        WaitlistRequestDto request = validRequest();
        request.setEmail("not-an-email");

        mockMvc.perform(post("/waitlist")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data[0].field").value("email"));
    }

    @Test
    void joinWaitlist_missingRequiredFields_returns400() throws Exception {
        WaitlistRequestDto request = new WaitlistRequestDto();

        mockMvc.perform(post("/waitlist")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
