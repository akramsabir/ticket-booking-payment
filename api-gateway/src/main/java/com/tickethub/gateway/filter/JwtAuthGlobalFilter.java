package com.tickethub.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tickethub.gateway.config.GatewayProperties;
import com.tickethub.gateway.security.AccessLevel;
import com.tickethub.gateway.security.AccessRuleMatcher;
import com.tickethub.gateway.security.AuthenticatedUser;
import com.tickethub.gateway.security.JwtAuthenticator;
import com.tickethub.gateway.security.PathSanitizer;
import com.tickethub.gateway.web.GatewayResponses;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;


/**
 * Security at the edge. For every request:
 *   1. refuses odd-looking paths (../, //, %2e ...)
 *   2. finds what the URL needs (gateway.security.rules): PUBLIC / AUTHENTICATED / ADMIN / DENY
 *   3. for non-public URLs verifies the Bearer token that user-service issued (signature + expiry)
 *   4. forwards the verified identity to the service as X-User-Id / X-User-Email / X-User-Roles
 *
 * The Authorization header is forwarded untouched: booking-service still checks the token itself and enforces
 * "customer only sees own bookings". The gateway is the first lock, not the only one.
 * Any X-User-* header sent by the CALLER is deleted first, so identity can never be faked from outside.
 */
@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_EMAIL = "X-User-Email";
    public static final String HEADER_USER_ROLES = "X-User-Roles";

    private static final Logger log = LoggerFactory.getLogger(JwtAuthGlobalFilter.class);

    private final AccessRuleMatcher matcher;
    private final JwtAuthenticator authenticator;
    private final ObjectMapper mapper;

    @org.springframework.beans.factory.annotation.Autowired
    public JwtAuthGlobalFilter(GatewayProperties properties, ObjectMapper mapper) {
        this(new AccessRuleMatcher(properties.getSecurity().getRules(), properties.getSecurity().getDefaultAccess()),
                new JwtAuthenticator(properties.getSecurity().getJwtSecret()),
                mapper);
    }

    JwtAuthGlobalFilter(AccessRuleMatcher matcher, JwtAuthenticator authenticator, ObjectMapper mapper) {
        this.matcher = matcher;
        this.authenticator = authenticator;
        this.mapper = mapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        if (PathSanitizer.isSuspicious(request.getURI().getRawPath())) {
            return GatewayResponses.error(exchange, mapper, HttpStatus.BAD_REQUEST, "Invalid request path");
        }

        AccessLevel access = matcher.resolve(request.getMethod().name(), request.getPath().value());
        if (access == AccessLevel.DENY) {
            return GatewayResponses.error(exchange, mapper, HttpStatus.FORBIDDEN,
                    "This resource is not available through the gateway");
        }

        // Never trust identity headers coming from outside
        ServerHttpRequest.Builder builder = request.mutate().headers(headers -> {
            headers.remove(HEADER_USER_ID);
            headers.remove(HEADER_USER_EMAIL);
            headers.remove(HEADER_USER_ROLES);
        });

        if (access == AccessLevel.PUBLIC) {
            return chain.filter(exchange.mutate().request(builder.build()).build());
        }

        String token = bearerToken(request);
        if (token == null) {
            return unauthorized(exchange, "Authentication is required");
        }

        AuthenticatedUser user;
        try {
            user = authenticator.authenticate(token);
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Rejected token for {} {}: {}", request.getMethod(), request.getPath().value(), ex.getMessage());
            return unauthorized(exchange, "The token is invalid or expired, please log in again");
        }

        if (access == AccessLevel.ADMIN && !user.hasRole("ADMIN")) {
            return GatewayResponses.error(exchange, mapper, HttpStatus.FORBIDDEN, "Administrator access is required");
        }

        builder.headers(headers -> {
            headers.set(HEADER_USER_ID, String.valueOf(user.userId()));
            headers.set(HEADER_USER_EMAIL, headerSafe(user.email()));
            headers.set(HEADER_USER_ROLES, headerSafe(String.join(",", user.roles())));
        });
        return chain.filter(exchange.mutate().request(builder.build()).build());
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        return GatewayResponses.error(exchange, mapper, HttpStatus.UNAUTHORIZED, message);
    }

    private static String bearerToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || header.length() < 8 || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = header.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    /** Header values must not contain line breaks or other control characters. */
    private static String headerSafe(String value) {
        return value == null ? "" : value.replaceAll("[\\p{Cntrl}]", "");
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
