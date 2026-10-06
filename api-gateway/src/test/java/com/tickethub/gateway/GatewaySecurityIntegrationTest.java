package com.tickethub.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Starts the real gateway (without Eureka) and checks the rules that do not need any other service to be running.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"eureka.client.enabled=false", "gateway.rate-limit.enabled=false"})
@AutoConfigureWebTestClient
class GatewaySecurityIntegrationTest {

    @Autowired
    private WebTestClient client;

    @Test
    void healthEndpointIsUp() {
        client.get().uri("/actuator/health").exchange().expectStatus().isOk();
    }

    @Test
    void bookingsWithoutToken_are401_withJsonBodyAndRequestId() {
        client.get().uri("/bookings/1").exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().exists("X-Request-Id")
                .expectHeader().exists("WWW-Authenticate")
                .expectBody().jsonPath("$.status").isEqualTo(401);
    }

    @Test
    void adminAreaWithoutToken_is401() {
        client.get().uri("/api/admin/users").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void notificationsWithoutToken_are401() {
        client.get().uri("/api/notifications").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void changingEventsWithoutToken_is401() {
        client.post().uri("/api/events").bodyValue("{}").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void paymentServiceInternalApi_isNotExposed() {
        client.get().uri("/api/v1/payments/1").exchange().expectStatus().isNotFound();
        client.post().uri("/api/v1/payments").bodyValue("{}").exchange().expectStatus().isNotFound();
    }
}
