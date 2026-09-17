package com.hdfclife.smartauth.resilience;

import com.hdfclife.smartauth.exception.ExternalServiceException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class LoginCircuitBreaker {

    private static final Logger logger =
            LoggerFactory.getLogger(LoginCircuitBreaker.class);

    /*
     * This is the Resilience4j CircuitBreaker object.
     *
     * It maintains the circuit state:
     *
     * CLOSED -> normal calls
     * OPEN -> calls are rejected
     * HALF_OPEN -> limited test calls
     */
    private final CircuitBreaker circuitBreaker;

    public LoginCircuitBreaker(
            @Qualifier("externalLoginCircuitBreaker")
            CircuitBreaker circuitBreaker) {

        this.circuitBreaker = circuitBreaker;
    }

    /*
     * Executes an external login operation through the CircuitBreaker.
     *
     * T means this method can work with any return type:
     *
     * LoginResponse
     * String
     * SomeOtherResponse
     *
     * CircuitBreakerCallable is our own functional interface.
     * It allows the operation to throw a checked Exception.
     */
    public <T> T executeExternalLogin(
            CircuitBreakerCallable<T> externalLoginCall
    ) throws Exception {

        try {

            /*
             * Give the external operation to Resilience4j.
             *
             * The CircuitBreaker decides whether the operation
             * is allowed to execute.
             */
            return circuitBreaker.executeSupplier(() -> {

                try {

                    logger.debug(
                            "Executing external login call with circuit breaker protection"
                    );

                    // Execute the actual external API operation.
                    return externalLoginCall.call();

                } catch (RuntimeException e) {

                    /*
                     * Keep existing RuntimeExceptions unchanged.
                     */
                    throw e;

                } catch (Exception e) {

                    /*
                     * Supplier cannot throw checked exceptions.
                     *
                     * Convert the checked exception into a RuntimeException
                     * so Resilience4j can receive it.
                     */
                    logger.error(
                            "Exception in external login call",
                            e
                    );

                    throw new RuntimeException(e);
                }
            });

        } catch (CallNotPermittedException e) {

            /*
             * This means the CircuitBreaker is OPEN.
             *
             * The external service was NOT called.
             *
             * Convert Resilience4j's exception into our application's
             * ExternalServiceException, which becomes HTTP 503.
             */
            logger.warn(
                    "Circuit breaker is open, rejecting request"
            );

            throw new ExternalServiceException(
                    "External service is currently unavailable " +
                    "due to circuit breaker being open"
            );

        } catch (RuntimeException e) {

            /*
             * If we wrapped a checked exception inside RuntimeException,
             * retrieve the original exception here.
             */
            if (e.getCause() instanceof Exception
                    && !(e.getCause() instanceof RuntimeException)) {

                throw (Exception) e.getCause();
            }

            // Otherwise keep the original RuntimeException.
            throw e;
        }
    }

    /*
     * Used by tests/monitoring to see the current circuit state.
     */
    public CircuitBreaker.State getState() {
        return circuitBreaker.getState();
    }

    /*
     * Reset the CircuitBreaker back to CLOSED.
     */
    public void reset() {
        circuitBreaker.reset();
        logger.info("Circuit breaker reset");
    }

    /*
     * Our own functional interface.
     *
     * It represents:
     * "an operation that returns T and may throw Exception."
     */
    @FunctionalInterface
    public interface CircuitBreakerCallable<T> {

        T call() throws Exception;
    }
}
