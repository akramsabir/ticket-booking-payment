package com.tickethub.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The START button of the whole application.
 *
 * @SpringBootApplication turns on three things at once:
 *  1. Component scanning  - finds all our @Service, @RestController, @Repository classes in this package and below
 *  2. Auto-configuration  - sets up Tomcat, database, Kafka, JSON automatically based on the libraries in pom.xml
 *  3. Configuration       - lets this class define extra settings if needed
 */
@SpringBootApplication
public class PaymentServiceApplication {

    public static void main(String[] args) {
        // Starts Spring, starts the web server (port 8083), connects to the database
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
