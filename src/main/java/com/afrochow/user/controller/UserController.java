package com.afrochow.user.controller;

import com.afrochow.common.response.ApiResponse;
import com.afrochow.security.model.CustomUserDetails;
import com.afrochow.user.dto.DeleteAccountRequestDto;
import com.afrochow.user.dto.UserResponseDto;
import com.afrochow.user.dto.UserUpdateRequestDto;
import com.afrochow.user.mapper.UserMapper;
import com.afrochow.user.model.User;
import com.afrochow.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@Tag(name = "User", description = "User profile management APIs")
public class UserController {

    private final UserService userService;
    private final UserMapper userMapper;


    //GET USERPROFILE
    @GetMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get current user profile")
    public ResponseEntity<ApiResponse<UserResponseDto>> getProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userService.requireAuthenticatedUser(userDetails);
        return ResponseEntity.ok(ApiResponse.success(userMapper.toResponseDto(user)));
    }



    //UPDATE USER PROFILE
    @PutMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update current user profile", description = "Update firstName, lastName, phone, or email")
    public ResponseEntity<ApiResponse<UserResponseDto>> updateProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UserUpdateRequestDto request) {

        User updated = userService.updateProfile(userDetails, request);
        return ResponseEntity.ok(ApiResponse.success("Profile updated successfully", userMapper.toResponseDto(updated)));
    }




    //DELETE USER ACCOUNT

    @DeleteMapping("/account")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Delete account", description = "Permanently delete the authenticated user's account after password confirmation")
    public ResponseEntity<ApiResponse<Void>> deleteAccount(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody DeleteAccountRequestDto request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        userService.deleteAccount(userDetails, request.getPassword(), httpRequest, httpResponse);

        return ResponseEntity.ok(ApiResponse.success(
                "Account scheduled for deletion. Sign back in within 30 days to reactivate."));
    }

}
