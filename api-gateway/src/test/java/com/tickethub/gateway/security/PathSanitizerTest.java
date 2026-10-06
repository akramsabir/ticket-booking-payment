package com.tickethub.gateway.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PathSanitizerTest {

    @Test
    void normalPathsAreFine() {
        assertFalse(PathSanitizer.isSuspicious("/bookings/12/payment"));
        assertFalse(PathSanitizer.isSuspicious("/api/v1/shows/events/3"));
        assertFalse(PathSanitizer.isSuspicious("/razorpay-checkout-test.html"));
        assertFalse(PathSanitizer.isSuspicious("/"));
        assertFalse(PathSanitizer.isSuspicious(null));
    }

    @Test
    void traversalAndAmbiguousFormsAreRefused() {
        assertTrue(PathSanitizer.isSuspicious("/api/events/../admin/users"));
        assertTrue(PathSanitizer.isSuspicious("/api/events/./x"));
        assertTrue(PathSanitizer.isSuspicious("/api//events"));
        assertTrue(PathSanitizer.isSuspicious("/api/events;jsessionid=1"));
        assertTrue(PathSanitizer.isSuspicious("/api/%2e%2e/admin"));
        assertTrue(PathSanitizer.isSuspicious("/api/events%2Fx"));
        assertTrue(PathSanitizer.isSuspicious("/api/events\\x"));
        assertTrue(PathSanitizer.isSuspicious("/api/%252e%252e/admin"));
    }
}
