package com.tickethub.gateway.security;

/** What a caller needs to use a URL. */
public enum AccessLevel {
    /** Anyone, no token needed. */
    PUBLIC,
    /** Any valid token (CUSTOMER or ADMIN). */
    AUTHENTICATED,
    /** A valid token that has the ADMIN role. */
    ADMIN,
    /** Nobody, the gateway refuses the request with 403. */
    DENY
}
