package com.tickethub.booking.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tickethub.booking.dto.response.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * The security rules of booking-service. There is no "off switch" any more: every booking endpoint needs a valid login.
 *
 *   URL level   (this class)          : must be logged in (a valid JWT from user-service), otherwise 401.
 *   Method level (@PreAuthorize on the controller, switched on by @EnableMethodSecurity):
 *                                       must have the right ROLE (e.g. CUSTOMER to book) and, for a single booking,
 *                                       must be its OWNER or an ADMIN (see BookingAccess), otherwise 403.
 *
 * 401 = "I do not know who you are" (no token / bad token / expired token).
 * 403 = "I know who you are, but you may not do this".
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)   // no cookies/sessions: the token is sent explicitly, so CSRF does not apply
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // public: health checks, API docs, and the developer checkout page (the page itself holds no secrets;
                        // the API calls it makes still need a token)
                        .requestMatchers("/actuator/health", "/actuator/info",
                                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                                "/razorpay-checkout-test.html").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) -> writeError(request, response, HttpStatus.UNAUTHORIZED,
                                "Authentication is required: send a valid, non-expired token as 'Authorization: Bearer <token>'"))
                        .accessDeniedHandler((request, response, e) -> writeError(request, response, HttpStatus.FORBIDDEN,
                                "You do not have permission to access this resource")))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** Same JSON shape as every other error of this service (ApiErrorResponse), so clients handle one format. */
    private void writeError(HttpServletRequest request, HttpServletResponse response,
                            HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(request.getRequestURI())
                .build());
    }
}
