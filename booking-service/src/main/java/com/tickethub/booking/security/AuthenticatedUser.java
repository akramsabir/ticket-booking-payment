package com.tickethub.booking.security;

import io.jsonwebtoken.JwtException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * WHO is calling. Built from the JWT that user-service issued, and stored as the "principal" of the
 * Spring Security login for the duration of one request.
 *
 * The important field is userId: it is the ONLY source of "which customer is this?". We never take the customer from
 * the request body or URL, because the caller controls those and could simply write somebody else's id.
 */
public record AuthenticatedUser(Long userId, String email, Set<String> roles) {

    public static final String ROLE_CUSTOMER = "ROLE_CUSTOMER";
    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    public boolean isAdmin() {
        return roles.contains(ROLE_ADMIN);
    }

    public boolean isCustomer() {
        return roles.contains(ROLE_CUSTOMER);
    }

    /**
     * Turns the claims of an already signature-checked JWT into an AuthenticatedUser.
     * Expects what user-service puts in the token: "userId" (number), "sub" (email), "roles" (list like ["ROLE_CUSTOMER"]).
     *
     * @throws JwtException when the token has no usable userId - e.g. an OLD token from before userId existed.
     *                      The filter treats that exactly like an invalid token (401, "log in again").
     */
    public static AuthenticatedUser fromClaims(Map<String, Object> claims) {
        Long userId = readUserId(claims.get("userId"));
        String email = claims.get("sub") instanceof String s ? s : null;
        return new AuthenticatedUser(userId, email, readRoles(claims.get("roles")));
    }

    /** The logged-in user of the current request, if any (empty for background jobs and anonymous calls). */
    public static Optional<AuthenticatedUser> current() {
        return from(SecurityContextHolder.getContext().getAuthentication());
    }

    public static Optional<AuthenticatedUser> from(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    // JSON numbers come back as Integer for small values and Long for big ones; be tolerant of both (and of "42").
    private static Long readUserId(Object raw) {
        if (raw instanceof Number n) {
            return n.longValue();
        }
        if (raw instanceof String s) {
            try {
                return Long.valueOf(s.trim());
            } catch (NumberFormatException ignored) {
                // fall through to the exception below
            }
        }
        throw new JwtException("Token has no valid userId claim - please log in again");
    }

    private static Set<String> readRoles(Object raw) {
        Set<String> roles = new HashSet<>();
        if (raw instanceof Collection<?> items) {
            for (Object item : items) {
                if (item != null) {
                    String role = item.toString().trim();
                    // Spring's hasRole('CUSTOMER') looks for "ROLE_CUSTOMER": add the prefix if a token omits it
                    roles.add(role.startsWith("ROLE_") ? role : "ROLE_" + role);
                }
            }
        }
        return Set.copyOf(roles);
    }

    /** Short and free of personal data, because this ends up in logs and in the audit columns. */
    @Override
    public String toString() {
        return "user:" + userId;
    }
}
