package com.tickethub.gateway.security;

import java.util.List;

/** The caller, read from a verified token. Roles look like ROLE_CUSTOMER / ROLE_ADMIN (as user-service writes them). */
public record AuthenticatedUser(long userId, String email, List<String> roles) {

    public boolean hasRole(String requiredRole) {
        if (roles == null) return false;
        String cleanRole = requiredRole.startsWith("ROLE_") ? requiredRole.substring(5) : requiredRole;
        return roles.stream().anyMatch(r ->
                r.equalsIgnoreCase(cleanRole) || r.equalsIgnoreCase("ROLE_" + cleanRole)
        );
    }
}
