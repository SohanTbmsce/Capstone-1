package com.hdfclife.smartauth.resilience;

import com.hdfclife.smartauth.exception.ExternalServiceException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * LoginFallbackHandler provides fallback behavior when the external login service fails.
 *
 * Responsibility: Handle only external service failures (circuit open, service down, etc).
 *
 * MUST NOT:
 * - authenticate the user
 * - return success
 * - generate JWT
 * - return fake credentials
 * - bypass authentication
 * - convert invalid credentials into success
 *
 * Fallback response: HTTP 503 Service Unavailable
 */
@Component
public class LoginFallbackHandler {

    private static final Logger logger = LoggerFactory.getLogger(LoginFallbackHandler.class);

    /**
     * Handle fallback when external login service is unavailable.
     *
     * @param username the username attempting login
     * @param throwable the exception that triggered the fallback
     * @throws ExternalServiceException always, indicating service unavailable
     */
    public void handleExternalServiceFailure(String username, Throwable throwable) {
        if (throwable instanceof CallNotPermittedException) {
            logger.warn("Circuit breaker is OPEN for user: {}. Service temporarily unavailable.", username);
            throw new ExternalServiceException("External authentication service is temporarily unavailable. Circuit breaker is OPEN.");
        }

        logger.error("External login service failed for user: {}. Exception: {}", username, throwable.getMessage());
        throw new ExternalServiceException("External authentication service failed: " + throwable.getMessage(), throwable);
    }
}
