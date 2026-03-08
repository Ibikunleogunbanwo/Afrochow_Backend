package com.afrochow.security.controller;

import com.afrochow.security.dto.ActiveSessionDto;
import com.afrochow.security.Services.RefreshTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for managing user sessions
 *
 * Endpoints:
 * - GET /sessions - Get all active sessions for the authenticated user
 * - DELETE /sessions/{tokenId} - Revoke a specific session
 */
@RestController
@RequestMapping("/sessions")
@Tag(name = "User Sessions", description = "User session management endpoints")
public class UserSessionController {

    private final RefreshTokenService refreshTokenService;

    public UserSessionController(RefreshTokenService refreshTokenService) {
        this.refreshTokenService = refreshTokenService;
    }

    /**
     * Get all active sessions for the authenticated user
     *
     * @param userDetails Authenticated user details
     * @return List of active sessions
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get active sessions", description = "Get all active sessions for the authenticated user")
    public ResponseEntity<List<ActiveSessionDto>> getActiveSessions(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        String username = userDetails.getUsername();
        List<ActiveSessionDto> sessions = refreshTokenService.getActiveSessions(username);
        return ResponseEntity.ok(sessions);
    }

    /**
     * Revoke a specific session
     *
     * @param tokenId ID of the session to revoke
     * @param userDetails Authenticated user details
     * @return Success message
     */
    @DeleteMapping("/{tokenId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Revoke session", description = "Revoke a specific session by token ID")
    public ResponseEntity<String> revokeSession(
            @PathVariable Long tokenId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        String username = userDetails.getUsername();
        refreshTokenService.revokeSession(username, tokenId);
        return ResponseEntity.ok("Session revoked successfully");
    }
}
