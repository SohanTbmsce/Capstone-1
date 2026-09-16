package com.hdfclife.smartauth.service;

import com.hdfclife.smartauth.exception.ExternalServiceException;
import com.hdfclife.smartauth.exception.InvalidCredentialsException;
import com.hdfclife.smartauth.resilience.LoginCircuitBreaker;
import com.hdfclife.smartauth.resilience.LoginFallbackHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * ExternalLoginService is a TEMPORARY mock of an external authentication service.
 *
 * It exists only because the final authentication implementation may not yet be
 * available from other developers.
 *
 * Responsibility:
 * - Mock external authentication for testing resilience module
 * - INTERNALLY use LoginCircuitBreaker (never externally)
 * - Do NOT use LoginRateLimiter (rate limiter is separate concern)
 * - Throw InvalidCredentialsException for wrong password (NOT a circuit failure)
 * - Throw ExternalServiceException for service unavailability (IS a circuit failure)
 *
 * IMPORTANT: Do NOT authenticate based on this temporary mock in final implementation.
 * This will be replaced with real external service integration.
 *
 * Temporary valid credentials:
 * - username = "user"
 * - password = "password"
 *
 * For testing service failures:
 * - username = "serviceDown" (simulates external service failure)
 */
@Service
public class ExternalLoginService {

    private static final Logger logger = LoggerFactory.getLogger(ExternalLoginService.class);

    private static final String VALID_USERNAME = "user";
    private static final String VALID_PASSWORD = "password";
    private static final String SERVICE_DOWN_USERNAME = "serviceDown";

    private final LoginCircuitBreaker loginCircuitBreaker;
    private final LoginFallbackHandler loginFallbackHandler;

    public ExternalLoginService(LoginCircuitBreaker loginCircuitBreaker,
                               LoginFallbackHandler loginFallbackHandler) {
        this.loginCircuitBreaker = loginCircuitBreaker;
        this.loginFallbackHandler = loginFallbackHandler;
    }

    /**
     * Validate external login with circuit breaker protection.
     *
     * @param username the username
     * @param password the password
     * @return true if credentials are valid
     * @throws InvalidCredentialsException if credentials are invalid (NOT a circuit failure)
     * @throws ExternalServiceException if external service is unavailable (IS a circuit failure)
     */
    public boolean validateExternalLogin(String username, String password) throws Exception {
        return loginCircuitBreaker.executeExternalLogin(() ->
            performExternalLoginValidation(username, password)
        );
    }

    /**
     * Internal method that performs the actual external login validation.
     * This is wrapped by the circuit breaker.
     *
     * @param username the username
     * @param password the password
     * @return true if credentials are valid
     * @throws InvalidCredentialsException if credentials are invalid (NOT counted as circuit failure)
     * @throws ExternalServiceException if external service fails (counted as circuit failure)
     */
    private boolean performExternalLoginValidation(String username, String password) {
        logger.debug("Performing external login validation for username: {}", username);

        // Simulate external service failure
        if (SERVICE_DOWN_USERNAME.equals(username)) {
            logger.error("Simulating external service failure for username: {}", username);
            throw new ExternalServiceException("External authentication service is currently unavailable");
        }

        // Validate credentials
        // IMPORTANT: This is TEMPORARY mock credential validation.
        // Final implementation will delegate to real external service.
        if (VALID_USERNAME.equals(username) && VALID_PASSWORD.equals(password)) {
            logger.info("External login successful for username: {}", username);
            return true;
        }

        // Invalid credentials - NOT a circuit breaker failure
        // Do NOT throw ExternalServiceException here
        logger.warn("External login failed: invalid credentials for username: {}", username);
        throw new InvalidCredentialsException("Invalid username or password");
    }
}
