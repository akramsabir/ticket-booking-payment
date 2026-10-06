package com.tickethub.userservice.dto;

import com.tickethub.userservice.entity.User;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** What we send back to clients. It never contains the password hash. */
public record UserResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        String phone,
        String status,
        Set<String> roles,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime lastLoginAt,
        String createdBy,
        String updatedBy) {

    public static UserResponse from(User u) {
        Set<String> roles = u.getUserRoles().stream()
                .map(ur -> ur.getRole().getName().name())
                .collect(Collectors.toCollection(TreeSet::new));
        return new UserResponse(u.getId(), u.getFirstName(), u.getLastName(), u.getEmail(), u.getPhone(),
                u.getStatus().name(), roles, u.getCreatedAt(), u.getUpdatedAt(), u.getLastLoginAt(),
                u.getCreatedBy(), u.getUpdatedBy());
    }
}
