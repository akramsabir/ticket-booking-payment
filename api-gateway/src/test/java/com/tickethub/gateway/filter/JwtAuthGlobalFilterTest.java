package com.tickethub.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tickethub.gateway.config.GatewayProperties;
import com.tickethub.gateway.security.AccessLevel;
import com.tickethub.gateway.security.AccessRuleMatcher;
import com.tickethub.gateway.security.JwtAuthenticator;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class JwtAuthGlobalFilterTest {

    private static final String SECRET = "unit-test-secret-key-0123456789-abcdefghij";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private JwtAuthGlobalFilter filter;
    private final AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
    private final GatewayFilterChain chain = exchange -> {
        forwarded.set(exchange);
        return Mono.empty();
    };

    private static GatewayProperties.AccessRule rule(List<String> methods, List<String> paths, AccessLevel access) {
        GatewayProperties.AccessRule rule = new GatewayProperties.AccessRule();
        rule.setMethods(methods);
        rule.setPaths(paths);
        rule.setAccess(access);
        return rule;
    }

    private static String token(List<String> roles, long expiresInMillis) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject("ravi@example.com")
                .claim("userId", 7L)
                .claim("roles", roles)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expiresInMillis))
                .signWith(KEY)
                .compact();
    }

    @BeforeEach
    void setUp() {
        AccessRuleMatcher matcher = new AccessRuleMatcher(List.of(
                rule(List.of("POST"), List.of("/api/auth/login"), AccessLevel.PUBLIC),
                rule(List.of(), List.of("/api/admin/**"), AccessLevel.ADMIN),
                rule(List.of(), List.of("/internal/**"), AccessLevel.DENY),
                rule(List.of(), List.of("/bookings/**"), AccessLevel.AUTHENTICATED)
        ), AccessLevel.AUTHENTICATED);
        filter = new JwtAuthGlobalFilter(matcher, new JwtAuthenticator(SECRET), new ObjectMapper());
    }

    private int run(MockServerHttpRequest request) {
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        filter.filter(exchange, chain).block();
        return exchange.getResponse().getStatusCode() == null ? 200 : exchange.getResponse().getStatusCode().value();
    }

    @Test
    void publicUrl_passesWithoutToken_andSpoofedIdentityHeadersAreRemoved() {
        int status = run(MockServerHttpRequest.post("/api/auth/login")
                .header("X-User-Id", "999").header("X-User-Roles", "ROLE_ADMIN").build());

        assertEquals(200, status);
        assertNotNull(forwarded.get());
        assertNull(forwarded.get().getRequest().getHeaders().getFirst("X-User-Id"));
        assertNull(forwarded.get().getRequest().getHeaders().getFirst("X-User-Roles"));
    }

    @Test
    void protectedUrl_withoutToken_is401() {
        forwarded.set(null);
        assertEquals(401, run(MockServerHttpRequest.get("/bookings/1").build()));
        assertNull(forwarded.get());
    }

    @Test
    void protectedUrl_withExpiredToken_is401() {
        forwarded.set(null);
        String expired = token(List.of("ROLE_CUSTOMER"), -60_000);
        assertEquals(401, run(MockServerHttpRequest.get("/bookings/1").header("Authorization", "Bearer " + expired).build()));
        assertNull(forwarded.get());
    }

    @Test
    void protectedUrl_withGarbageToken_is401() {
        assertEquals(401, run(MockServerHttpRequest.get("/bookings/1").header("Authorization", "Bearer abc.def.ghi").build()));
    }

    @Test
    void protectedUrl_withValidToken_isForwardedWithIdentityHeaders_andTheTokenItself() {
        String jwt = token(List.of("ROLE_CUSTOMER"), 60_000);
        int status = run(MockServerHttpRequest.get("/bookings/1")
                .header("Authorization", "Bearer " + jwt)
                .header("X-User-Id", "999")
                .build());

        assertEquals(200, status);
        assertEquals("7", forwarded.get().getRequest().getHeaders().getFirst("X-User-Id"));
        assertEquals("ravi@example.com", forwarded.get().getRequest().getHeaders().getFirst("X-User-Email"));
        assertEquals("ROLE_CUSTOMER", forwarded.get().getRequest().getHeaders().getFirst("X-User-Roles"));
        assertEquals("Bearer " + jwt, forwarded.get().getRequest().getHeaders().getFirst("Authorization"));
    }

    @Test
    void adminUrl_withCustomerToken_is403() {
        String jwt = token(List.of("ROLE_CUSTOMER"), 60_000);
        assertEquals(403, run(MockServerHttpRequest.get("/api/admin/users").header("Authorization", "Bearer " + jwt).build()));
    }

    @Test
    void adminUrl_withAdminToken_isForwarded() {
        String jwt = token(List.of("ROLE_ADMIN"), 60_000);
        assertEquals(200, run(MockServerHttpRequest.get("/api/admin/users").header("Authorization", "Bearer " + jwt).build()));
    }

    @Test
    void deniedUrl_is403_evenWithAnAdminToken() {
        String jwt = token(List.of("ROLE_ADMIN"), 60_000);
        assertEquals(403, run(MockServerHttpRequest.get("/internal/x").header("Authorization", "Bearer " + jwt).build()));
    }

    @Test
    void pathTraversal_is400() {
        assertEquals(400, run(MockServerHttpRequest.get("/api/auth/../admin/users").build()));
    }
}
