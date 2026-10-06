package com.tickethub.booking.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks the translation "claims of a user-service JWT" -> "who is calling".
 * (The signature/expiry check itself is done by the jjwt library inside JwtAuthenticationFilter.)
 */
class AuthenticatedUserTest {

    /** Exactly the shape user-service produces: sub=email, userId=number, roles=["ROLE_..."]. */
    private Map<String, Object> userServiceClaims() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "ravi@example.com");
        claims.put("userId", 42);                       // jjwt hands small numbers back as Integer
        claims.put("roles", List.of("ROLE_CUSTOMER"));
        return claims;
    }

    @Test
    void customerToken_becomesACustomerWithTheRightId() {
        AuthenticatedUser user = AuthenticatedUser.fromClaims(userServiceClaims());

        assertEquals(42L, user.userId());
        assertEquals("ravi@example.com", user.email());
        assertTrue(user.isCustomer());
        assertFalse(user.isAdmin());
    }

    @Test
    void adminToken_isAdmin() {
        Map<String, Object> claims = userServiceClaims();
        claims.put("roles", List.of("ROLE_ADMIN", "ROLE_CUSTOMER"));

        AuthenticatedUser user = AuthenticatedUser.fromClaims(claims);

        assertTrue(user.isAdmin());
        assertTrue(user.isCustomer());
    }

    @Test
    void userIdAsLongOrNumericString_isAccepted() {
        Map<String, Object> asLong = userServiceClaims();
        asLong.put("userId", 5_000_000_000L);
        assertEquals(5_000_000_000L, AuthenticatedUser.fromClaims(asLong).userId());

        Map<String, Object> asString = userServiceClaims();
        asString.put("userId", " 7 ");
        assertEquals(7L, AuthenticatedUser.fromClaims(asString).userId());
    }

    @Test
    void oldTokenWithoutUserId_isRejected_soTheUserMustLogInAgain() {
        Map<String, Object> claims = userServiceClaims();
        claims.remove("userId");

        assertThrows(JwtException.class, () -> AuthenticatedUser.fromClaims(claims));
    }

    @Test
    void garbageUserId_isRejected() {
        Map<String, Object> claims = userServiceClaims();
        claims.put("userId", "not-a-number");
        assertThrows(JwtException.class, () -> AuthenticatedUser.fromClaims(claims));

        claims.put("userId", List.of(1, 2));
        assertThrows(JwtException.class, () -> AuthenticatedUser.fromClaims(claims));
    }

    @Test
    void rolesWithoutThePrefix_getItAdded_andMissingRolesMeanNoPermissions() {
        Map<String, Object> claims = userServiceClaims();
        claims.put("roles", List.of("CUSTOMER"));
        assertTrue(AuthenticatedUser.fromClaims(claims).isCustomer());

        claims.remove("roles");
        AuthenticatedUser none = AuthenticatedUser.fromClaims(claims);
        assertTrue(none.roles().isEmpty());
        assertFalse(none.isCustomer());
    }

    @Test
    void toString_neverContainsTheEmail() {
        assertEquals("user:42", AuthenticatedUser.fromClaims(userServiceClaims()).toString());
    }
}
