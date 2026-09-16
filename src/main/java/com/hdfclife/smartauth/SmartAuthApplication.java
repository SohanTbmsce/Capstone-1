package com.hdfclife.smartauth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SmartAuthApplication is the Spring Boot entry point.
 *
 * This application implements the Resilience4j module for the smart authentication backend.
 * It provides:
 * - Login Rate Limiter (by username + IP)
 * - Login Circuit Breaker (for external service protection)
 * - External Login Fallback
 * - Temporary mock external login endpoint for testing
 */
@SpringBootApplication
public class SmartAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartAuthApplication.class, args);
    }
}
