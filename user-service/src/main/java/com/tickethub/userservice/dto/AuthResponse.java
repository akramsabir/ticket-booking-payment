package com.tickethub.userservice.dto;

public record AuthResponse(String token, String tokenType, long expiresInMs, UserResponse user) {
}
