package com.tickethub.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Verifies tokens issued by user-service: signature + expiry, then reads the claims
 * subject = email, userId = users.id, roles = ["ROLE_CUSTOMER"] or ["ROLE_ADMIN"].
 * The signing algorithm is chosen from the secret's length exactly like user-service does (Keys.hmacShaKeyFor).
 */
@Component
public class JwtAuthenticator {

    private final SecretKey key;

    public JwtAuthenticator(@Value("${gateway.security.jwt-secret}") String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "gateway.security.jwt-secret (env JWT_SECRET) must be at least 32 characters and equal to user-service's app.jwt.secret");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @throws JwtException             token is forged, tampered with, expired or malformed
     * @throws IllegalArgumentException token is empty
     */
    public AuthenticatedUser authenticate(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();

        Long userId = toLong(claims.get("userId"));
        if (userId == null) {
            // Tokens issued before userId was added to user-service: the caller must log in again
            throw new JwtException("Token has no userId claim, please log in again");
        }
        String email = claims.getSubject() == null ? "" : claims.getSubject();

        List<String> roles = new ArrayList<>();
        Object rawRoles = claims.get("roles");
        if (rawRoles instanceof List<?> list) {
            for (Object role : list) {
                if (role != null) {
                    roles.add(role.toString());
                }
            }
        }
        return new AuthenticatedUser(userId, email, roles);
    }

    private static Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.valueOf(text.trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }
}