package com.tickethub.gateway.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Tokens are built the same way user-service's JwtService builds them. */
class JwtAuthenticatorTest {

    private static final String SECRET = "unit-test-secret-key-0123456789-abcdefghij";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private final JwtAuthenticator authenticator = new JwtAuthenticator(SECRET);

    private static String token(SecretKey key, Object userId, List<String> roles, long expiresInMillis) {
        long now = System.currentTimeMillis();
        var builder = Jwts.builder()
                .subject("ravi@example.com")
                .claim("roles", roles)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expiresInMillis))
                .signWith(key);
        if (userId != null) {
            builder.claim("userId", userId);
        }
        return builder.compact();
    }

    @Test
    void validToken_givesUserIdEmailAndRoles() {
        AuthenticatedUser user = authenticator.authenticate(token(KEY, 7L, List.of("ROLE_CUSTOMER"), 60_000));

        assertEquals(7L, user.userId());
        assertEquals("ravi@example.com", user.email());
        assertTrue(user.hasRole("CUSTOMER"));
        assertFalse(user.hasRole("ADMIN"));
    }

    @Test
    void adminRoleIsRecognised() {
        AuthenticatedUser user = authenticator.authenticate(token(KEY, 1, List.of("ROLE_ADMIN"), 60_000));
        assertTrue(user.hasRole("ADMIN"));
    }

    @Test
    void expiredToken_isRejected() {
        assertThrows(JwtException.class,
                () -> authenticator.authenticate(token(KEY, 7L, List.of("ROLE_CUSTOMER"), -60_000)));
    }

    @Test
    void tokenSignedWithAnotherSecret_isRejected() {
        SecretKey other = Keys.hmacShaKeyFor("another-secret-key-0123456789-abcdefghijkl".getBytes(StandardCharsets.UTF_8));
        assertThrows(JwtException.class,
                () -> authenticator.authenticate(token(other, 7L, List.of("ROLE_CUSTOMER"), 60_000)));
    }

    @Test
    void tokenWithoutUserId_isRejected() {
        assertThrows(JwtException.class,
                () -> authenticator.authenticate(token(KEY, null, List.of("ROLE_CUSTOMER"), 60_000)));
    }

    @Test
    void garbage_isRejected() {
        assertThrows(JwtException.class, () -> authenticator.authenticate("not.a.jwt"));
    }

    @Test
    void tooShortSecret_failsFast() {
        assertThrows(IllegalStateException.class, () -> new JwtAuthenticator("short"));
    }
}
