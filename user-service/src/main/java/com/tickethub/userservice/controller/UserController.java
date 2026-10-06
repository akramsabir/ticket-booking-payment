package com.tickethub.userservice.controller;

import com.tickethub.userservice.dto.ChangePasswordRequest;
import com.tickethub.userservice.dto.UpdateProfileRequest;
import com.tickethub.userservice.dto.UserResponse;
import com.tickethub.userservice.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Endpoints for the logged-in user to manage their own account. */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserResponse me(Authentication auth) {
        return userService.getMe(auth.getName());
    }

    @PutMapping("/me")
    public UserResponse updateMe(Authentication auth, @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateMe(auth.getName(), request);
    }

    @PutMapping("/me/password")
    public ResponseEntity<Map<String, String>> changePassword(Authentication auth,
                                                             @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(auth.getName(), request);
        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
    }
}
