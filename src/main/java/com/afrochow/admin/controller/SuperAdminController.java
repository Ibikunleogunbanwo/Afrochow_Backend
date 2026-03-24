package com.afrochow.admin.controller;
import com.afrochow.common.ApiResponse;
import com.afrochow.common.enums.Role;
import com.afrochow.user.model.User;
import com.afrochow.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/superadmin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPERADMIN')")
@Tag(name = "Superadmin", description = "Superadmin-only operations")
public class SuperAdminController {

    private final UserRepository userRepository;

    @PatchMapping("/users/{publicUserId}/promote")
    @Operation(summary = "Promote to SUPERADMIN", description = "Promote an existing ADMIN user to SUPERADMIN")
    public ResponseEntity<ApiResponse<Void>> promoteToSuperAdmin(
            @PathVariable String publicUserId) {

        User user = userRepository.findByPublicUserId(publicUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        // Only ADMINs can be promoted — not regular users or vendors
        if (user.getRole() != Role.ADMIN) {
            throw new IllegalStateException("Only ADMIN users can be promoted to SUPERADMIN");
        }

        user.setRole(Role.SUPERADMIN);
        userRepository.save(user);

        return ResponseEntity.ok(ApiResponse.success("User promoted to SUPERADMIN successfully"));
    }

    @PatchMapping("/users/{publicUserId}/demote")
    @Operation(summary = "Demote SUPERADMIN", description = "Demote a SUPERADMIN back to ADMIN")
    public ResponseEntity<ApiResponse<Void>> demoteToAdmin(
            @PathVariable String publicUserId) {

        User user = userRepository.findByPublicUserId(publicUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        if (user.getRole() != Role.SUPERADMIN) {
            throw new IllegalStateException("User is not a SUPERADMIN");
        }

        user.setRole(Role.ADMIN);
        userRepository.save(user);

        return ResponseEntity.ok(ApiResponse.success("User demoted to ADMIN successfully"));
    }
}
