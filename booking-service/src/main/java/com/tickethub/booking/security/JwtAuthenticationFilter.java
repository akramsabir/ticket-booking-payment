package com.tickethub.booking.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * AUTHENTICATION: "who are you?" Runs once per request.
 *
 * If the request carries  Authorization: Bearer <token>  we check that the token
 *   1. was signed with the shared secret (so user-service really issued it and nobody edited it),
 *   2. has not expired,
 *   3. contains a userId and roles (what user-service puts there).
 * If all is well the caller is "logged in" for this one request. If anything is wrong we simply stay anonymous, and
 * SecurityConfig then answers 401 for every protected URL. No state is kept between requests (no sessions).
 *
 * The secret MUST be the same value as user-service's app.jwt.secret (env var JWT_SECRET).
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final SecretKey key;

    public JwtAuthenticationFilter(@Value("${booking.security.jwt-secret}") String secret) {
        // Throws a clear error at STARTUP when the secret is shorter than 32 bytes, instead of failing on the first request
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            try {
                // parseSignedClaims verifies the signature AND the expiry, and refuses unsigned ("alg: none") tokens
                Claims claims = Jwts.parser().verifyWith(key).build()
                        .parseSignedClaims(header.substring(BEARER.length()).trim())
                        .getPayload();

                AuthenticatedUser user = AuthenticatedUser.fromClaims(claims);
                List<SimpleGrantedAuthority> authorities = user.roles().stream()
                        .map(SimpleGrantedAuthority::new)
                        .toList();
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(user, null, authorities));
            } catch (JwtException | IllegalArgumentException e) {
                // Expired, tampered, wrong secret, malformed, or no userId: stay anonymous -> 401 further down the chain.
                log.debug("Rejected JWT for {} {}: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
