package com.tickethub.userservice.controller;

import com.tickethub.userservice.dto.AssignRolesRequest;
import com.tickethub.userservice.dto.UpdateStatusRequest;
import com.tickethub.userservice.dto.UserResponse;
import com.tickethub.userservice.entity.UserStatus;
import com.tickethub.userservice.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** ADMIN-only endpoints (also guarded by the URL rule in SecurityConfig). */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserResponse> list(@RequestParam(required = false) UserStatus status) {
        return userService.listUsers(status);
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable Long id) {
        return userService.getById(id);
    }

    @PatchMapping("/{id}/status")
    public UserResponse updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateStatusRequest request,
                                     Authentication auth) {
        return userService.updateStatus(id, request.status(), auth.getName());
    }

    @PutMapping("/{id}/roles")
    public UserResponse assignRoles(@PathVariable Long id, @Valid @RequestBody AssignRolesRequest request,
                                    Authentication auth) {
        return userService.assignRoles(id, request.roles(), auth.getName());
    }

    @DeleteMapping("/{id}")
    public UserResponse delete(@PathVariable Long id, Authentication auth) {
        return userService.deleteUser(id, auth.getName());
    }
}
