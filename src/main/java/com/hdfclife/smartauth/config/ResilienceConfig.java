package com.hdfclife.smartauth.config;

import com.hdfclife.smartauth.exception.ExternalServiceException;
import com.hdfclife.smartauth.exception.InvalidCredentialsException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * ResilienceConfig configures Resilience4j components.
 *
 * Includes:
 * - CircuitBreaker for external login service protection
 * - RateLimiter for login attempt rate limiting
 *
 * Configuration values are development/testing friendly for easy demonstration.
 */
@Configuration
public class ResilienceConfig {

    private static final Logger logger = LoggerFactory.getLogger(ResilienceConfig.class);

    /**
     * Configure the CircuitBreaker for external login service.
     *
     * IMPORTANT: InvalidCredentialsException is NOT counted as a failure.
     * Only ExternalServiceException counts as a failure.
     *
     * Configuration:
     * - failureRateThreshold: 50% (circuit opens after 50% failure rate)
     * - slidingWindowSize: 10 (track last 10 calls)
     * - minimumNumberOfCalls: 5 (need at least 5 calls before evaluating)
     * - waitDurationInOpenState: 30 seconds (wait before allowing retry)
     * - permittedNumberOfCallsInHalfOpenState: 3 (try 3 calls in half-open state)
     */
    @Bean
    public CircuitBreaker externalLoginCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50.0f)
                .slowCallRateThreshold(100.0f)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .slowCallDurationThreshold(Duration.ofSeconds(2))
                .permittedNumberOfCallsInHalfOpenState(3)
                .minimumNumberOfCalls(5)
                .slidingWindowSize(10)
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                // CRITICAL: InvalidCredentialsException is NOT a failure
                // Only ExternalServiceException is recorded as failure
                .recordExceptions(ExternalServiceException.class)
                .ignoreExceptions(InvalidCredentialsException.class)
                .build();

        CircuitBreaker circuitBreaker = CircuitBreaker.of("login-circuit-breaker", config);
        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> logger.info("Circuit breaker state transition: {} -> {}",
                        event.getStateTransition().getFromState(),
                        event.getStateTransition().getToState()))
                .onError(event -> logger.warn("Circuit breaker recorded failure: {}",
                        event.getThrowable().getMessage()))
                .onSuccess(event -> logger.debug("Circuit breaker success"));

        return circuitBreaker;
    }

    /**
     * Configure the RateLimiter for login attempts.
     *
     * This template is used by LoginRateLimiter to create per-key limiters.
     *
     * Configuration:
     * - limitForPeriod: 5 (allow 5 requests)
     * - limitRefreshPeriod: 1 minute (per minute)
     * - timeoutDuration: 0 (fail immediately if limit exceeded, don't wait)
     */
    @Bean
    public RateLimiter loginRateLimiterTemplate() {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(5)
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .timeoutDuration(Duration.ZERO)
                .build();

        RateLimiter rateLimiter = RateLimiter.of("login-rate-limiter-template", config);
        rateLimiter.getEventPublisher()
                .onSuccess(event -> logger.debug("Rate limiter: permission acquired"))
                .onFailure(event -> logger.warn("Rate limiter: permission denied"));

        return rateLimiter;
    }
}
