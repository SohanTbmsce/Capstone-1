package com.hdfclife.smartauth.resilience;

import com.hdfclife.smartauth.exception.ExternalServiceException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * LoginCircuitBreaker protects the external login service from repeated failures.
 *
 * It wraps ONLY the external service call, not the rate limiter, JWT generation,
 * token storage, or controller logic.
 *
 * Responsibility: Protect the external login call using circuit breaker pattern.
 *
 * Behavior:
 * - CLOSED: Normal operation, external calls proceed
 * - OPEN: Too many failures, external calls blocked, fallback triggered
 * - HALF_OPEN: Testing if external service recovered
 *
 * IMPORTANT: Invalid credentials do NOT count as circuit breaker failures.
 * Only ExternalServiceException counts as a failure.
 * InvalidCredentialsException is ignored by the circuit breaker.
 */
@Component
public class LoginCircuitBreaker {

    private static final Logger logger = LoggerFactory.getLogger(LoginCircuitBreaker.class);

    private final CircuitBreaker circuitBreaker;

    public LoginCircuitBreaker(@Qualifier("externalLoginCircuitBreaker") CircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    /**
     * Execute the external login call with circuit breaker protection.
     *
     * @param externalLoginCall the callable that performs the external login
     * @param <T> the return type
     * @return the result of the external login call
     * @throws Exception if the external service fails or circuit is open
     */
    @SuppressWarnings("unchecked")
    public <T> T executeExternalLogin(CircuitBreakerCallable<T> externalLoginCall) throws Exception {
        try {
            return circuitBreaker.executeSupplier(() -> {
                try {
                    logger.debug("Executing external login call with circuit breaker protection");
                    return externalLoginCall.call();
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    logger.error("Exception in external login call", e);
                    throw new RuntimeException(e);
                }
            });
        } catch (CallNotPermittedException e) {
            // Circuit is open
            logger.warn("Circuit breaker is open, rejecting request");
            throw new ExternalServiceException("External service is currently unavailable due to circuit breaker being open");
        } catch (RuntimeException e) {
            // Unwrap the original exception if it was wrapped
            if (e.getCause() instanceof Exception && !(e.getCause() instanceof RuntimeException)) {
                throw (Exception) e.getCause();
            }
            throw e;
        }
    }

    /**
     * Get the current state of the circuit breaker.
     *
     * @return the state (CLOSED, OPEN, HALF_OPEN)
     */
    public CircuitBreaker.State getState() {
        return circuitBreaker.getState();
    }

    /**
     * Reset the circuit breaker to CLOSED state.
     */
    public void reset() {
        circuitBreaker.reset();
        logger.info("Circuit breaker reset");
    }

    @FunctionalInterface
    public interface CircuitBreakerCallable<T> {
        T call() throws Exception;
    }
}
