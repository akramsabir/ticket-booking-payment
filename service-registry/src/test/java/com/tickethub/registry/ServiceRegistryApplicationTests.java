package com.tickethub.registry;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ServiceRegistryApplicationTests {

    @Test
    void contextLoads() {
        // The Eureka server starts with the configuration in application.yml
    }
}
