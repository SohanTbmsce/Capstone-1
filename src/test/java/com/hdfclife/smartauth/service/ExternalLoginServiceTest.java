package com.hdfclife.smartauth.service;

import com.hdfclife.smartauth.exception.ExternalServiceException;
import com.hdfclife.smartauth.exception.InvalidCredentialsException;
import com.hdfclife.smartauth.resilience.LoginCircuitBreaker;
import com.hdfclife.smartauth.resilience.LoginFallbackHandler;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class ExternalLoginServiceTest {

    private ExternalLoginService externalLoginService;
    private LoginCircuitBreaker loginCircuitBreaker;
    private LoginFallbackHandler loginFallbackHandler;

    @BeforeEach
    void setUp() {
        loginFallbackHandler = new LoginFallbackHandler();

        // Create a circuit breaker with low failure threshold for testing
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50.0f)
                .waitDurationInOpenState(Duration.ofSeconds(1))
                .minimumNumberOfCalls(2)
                .slidingWindowSize(5)
                .recordExceptions(ExternalServiceException.class)
                .ignoreExceptions(InvalidCredentialsException.class)
                .build();

        CircuitBreaker circuitBreaker = CircuitBreaker.of("test-circuit-breaker", config);
        loginCircuitBreaker = new LoginCircuitBreaker(circuitBreaker);

        externalLoginService = new ExternalLoginService(loginCircuitBreaker, loginFallbackHandler);
    }

    @Test
    void testValidCredentials() throws Exception {
        boolean result = externalLoginService.validateExternalLogin("user", "password");
        assertTrue(result, "Valid credentials should return true");
    }

    @Test
    void testInvalidPassword() throws Exception {
        assertThrows(InvalidCredentialsException.class,
                () -> externalLoginService.validateExternalLogin("user", "wrong"),
                "Invalid password should throw InvalidCredentialsException");
    }

    @Test
    void testInvalidUsername() throws Exception {
        assertThrows(InvalidCredentialsException.class,
                () -> externalLoginService.validateExternalLogin("unknown", "password"),
                "Invalid username should throw InvalidCredentialsException");
    }

    @Test
    void testServiceDownUsername() throws Exception {
        assertThrows(ExternalServiceException.class,
                () -> externalLoginService.validateExternalLogin("serviceDown", "anything"),
                "serviceDown username should throw ExternalServiceException");
    }

    @Test
    void testInvalidCredentialsDoNotIncrementCircuitBreakerFailures() throws Exception {
        // Invalid credentials should NOT count as circuit breaker failure
        for (int i = 0; i < 5; i++) {
            try {
                externalLoginService.validateExternalLogin("user", "wrong");
            } catch (InvalidCredentialsException e) {
                // Expected
            }
        }

        // Circuit breaker should still be CLOSED (because InvalidCredentialsException is ignored)
        assertEquals(CircuitBreaker.State.CLOSED, loginCircuitBreaker.getState(),
                "Circuit breaker should remain CLOSED after InvalidCredentialsException");

        // Valid credentials should still work
        assertTrue(externalLoginService.validateExternalLogin("user", "password"),
                "Valid credentials should work when circuit is closed");
    }

    @Test
    void testExternalServiceFailuresOpenCircuit() throws Exception {
        // Trigger enough external service failures to open the circuit
        for (int i = 0; i < 3; i++) {
            try {
                externalLoginService.validateExternalLogin("serviceDown", "anything");
            } catch (ExternalServiceException e) {
                // Expected
            }
        }

        // Circuit should be open or half-open
        CircuitBreaker.State state = loginCircuitBreaker.getState();
        assertTrue(state == CircuitBreaker.State.OPEN || state == CircuitBreaker.State.HALF_OPEN,
                "Circuit breaker should be OPEN or HALF_OPEN after external failures");
    }

    @Test
    void testCircuitBreakerThrowsExceptionWhenOpen() throws Exception {
        // Trigger enough failures to open the circuit
        for (int i = 0; i < 3; i++) {
            try {
                externalLoginService.validateExternalLogin("serviceDown", "anything");
            } catch (ExternalServiceException e) {
                // Expected
            }
        }

        // Wait a moment to ensure circuit is definitely open
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Even with valid credentials, should throw ExternalServiceException
        // because the circuit is open and fallback handler throws it
        assertThrows(ExternalServiceException.class,
                () -> externalLoginService.validateExternalLogin("user", "password"),
                "When circuit is open, should throw ExternalServiceException for any request");
    }
}

