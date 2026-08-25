package com.afrochow.waitlist.controller;

import com.afrochow.common.response.ApiResponse;
import com.afrochow.waitlist.dto.WaitlistRequestDto;
import com.afrochow.waitlist.dto.WaitlistResponseDto;
import com.afrochow.waitlist.service.WaitlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/waitlist")
@Tag(name = "Waitlist", description = "Public MVP waitlist endpoints")
public class WaitlistController {

    private final WaitlistService waitlistService;

    public WaitlistController(WaitlistService waitlistService) {
        this.waitlistService = waitlistService;
    }

    @PostMapping
    @Operation(summary = "Join waitlist", description = "Create or update a public MVP waitlist lead")
    public ResponseEntity<ApiResponse<WaitlistResponseDto>> joinWaitlist(
            @Valid @RequestBody WaitlistRequestDto request
    ) {
        WaitlistResponseDto response = waitlistService.join(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Waitlist entry saved", response));
    }
}
