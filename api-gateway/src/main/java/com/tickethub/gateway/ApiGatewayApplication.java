package com.tickethub.gateway;

import com.tickethub.gateway.config.GatewayProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * TicketHub API gateway - the ONE address clients talk to (default http://localhost:8090).
 *
 *   client ──▶ gateway ──▶ user-service / booking-service / event-service / payment (webhook only) / notification-service
 *
 * What it does for every request:
 *   1. gives it an X-Request-Id and writes one access-log line
 *   2. rate-limits it per client IP
 *   3. checks the JWT (signature + expiry) and the role the URL needs
 *   4. finds the target service (Eureka) and forwards the request, protected by a circuit breaker
 */
@SpringBootApplication
@EnableConfigurationProperties(GatewayProperties.class)
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
