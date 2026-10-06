package com.tickethub.gateway.security;

import com.tickethub.gateway.config.GatewayProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AccessRuleMatcherTest {

    private AccessRuleMatcher matcher;

    static GatewayProperties.AccessRule rule(List<String> methods, List<String> paths, AccessLevel access) {
        GatewayProperties.AccessRule rule = new GatewayProperties.AccessRule();
        rule.setMethods(methods);
        rule.setPaths(paths);
        rule.setAccess(access);
        return rule;
    }

    @BeforeEach
    void setUp() {
        matcher = new AccessRuleMatcher(List.of(
                rule(List.of("OPTIONS"), List.of("/**"), AccessLevel.PUBLIC),
                rule(List.of("POST"), List.of("/api/auth/login", "/api/auth/register"), AccessLevel.PUBLIC),
                rule(List.of(), List.of("/api/admin/**"), AccessLevel.ADMIN),
                rule(List.of("GET", "HEAD"), List.of("/api/events/**"), AccessLevel.PUBLIC),
                rule(List.of(), List.of("/api/events/**"), AccessLevel.ADMIN),
                rule(List.of(), List.of("/bookings/**"), AccessLevel.AUTHENTICATED)
        ), AccessLevel.AUTHENTICATED);
    }

    @Test
    void loginAndRegisterArePublic_butOnlyWithPost() {
        assertEquals(AccessLevel.PUBLIC, matcher.resolve("POST", "/api/auth/login"));
        assertEquals(AccessLevel.PUBLIC, matcher.resolve("post", "/api/auth/register"));
        assertEquals(AccessLevel.AUTHENTICATED, matcher.resolve("GET", "/api/auth/login"));
    }

    @Test
    void browsingEventsIsPublic_changingThemNeedsAdmin() {
        assertEquals(AccessLevel.PUBLIC, matcher.resolve("GET", "/api/events"));
        assertEquals(AccessLevel.PUBLIC, matcher.resolve("GET", "/api/events/5"));
        assertEquals(AccessLevel.ADMIN, matcher.resolve("POST", "/api/events"));
        assertEquals(AccessLevel.ADMIN, matcher.resolve("DELETE", "/api/events/5"));
    }

    @Test
    void adminAreaNeedsAdmin() {
        assertEquals(AccessLevel.ADMIN, matcher.resolve("GET", "/api/admin/users"));
        assertEquals(AccessLevel.ADMIN, matcher.resolve("PUT", "/api/admin/users/3/roles"));
    }

    @Test
    void preflightIsAlwaysPublic() {
        assertEquals(AccessLevel.PUBLIC, matcher.resolve("OPTIONS", "/bookings/1"));
        assertEquals(AccessLevel.PUBLIC, matcher.resolve("OPTIONS", "/api/admin/users"));
    }

    @Test
    void bookingsNeedAToken_andUnknownPathsFallBackToTheDefault() {
        assertEquals(AccessLevel.AUTHENTICATED, matcher.resolve("POST", "/bookings"));
        assertEquals(AccessLevel.AUTHENTICATED, matcher.resolve("GET", "/bookings/9/payment"));
        assertEquals(AccessLevel.AUTHENTICATED, matcher.resolve("GET", "/something/else"));
    }
}
