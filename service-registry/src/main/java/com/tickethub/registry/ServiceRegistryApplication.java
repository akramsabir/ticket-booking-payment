package com.tickethub.registry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * TicketHub service registry (Eureka server).
 *
 * Every microservice registers itself here under its spring.application.name
 * (user-service, booking-service, payment-service, notification-service ...).
 * The API gateway asks this registry where a service currently lives, so no service needs a hard-coded URL.
 *
 * Dashboard: http://localhost:8761
 */
@SpringBootApplication
@EnableEurekaServer
public class ServiceRegistryApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceRegistryApplication.class, args);
    }
}
