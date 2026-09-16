package com.hdfclife.smartauth.resilience;

import com.hdfclife.smartauth.exception.RateLimitExceededException;
import io.github.resilience4j.ratelimiter.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LoginRateLimiter enforces rate limiting on login attempts.
 *
 * It limits based on a composite key of username + client IP address.
 * Each unique username + IP combination has its own independent rate limiter.
 *
 * Responsibility: Determine if a login attempt from a specific username + IP is allowed.
 *
 * MUST NOT:
 * - validate username
 * - validate password
 * - know whether credentials are correct
 * - call the external service
 * - generate JWT
 * - contain authentication business logic
 */
@Component
public class LoginRateLimiter {

    private static final Logger logger = LoggerFactory.getLogger(LoginRateLimiter.class);

    private final Map<String, RateLimiter> limiters;
    private final RateLimiter rateLimiterTemplate;

    public LoginRateLimiter(RateLimiter rateLimiterTemplate) {
        this.limiters = new ConcurrentHashMap<>();
        this.rateLimiterTemplate = rateLimiterTemplate;
    }

    /**
     * Check if the login attempt is within rate limits.
     *
     * @param username the username attempting login
     * @param clientIp the client IP address
     * @throws RateLimitExceededException if the rate limit is exceeded
     */
    public void checkRateLimit(String username, String clientIp) {
        String key = createKey(username, clientIp);
        RateLimiter limiter = limiters.computeIfAbsent(key, k -> {
            logger.debug("Creating new rate limiter for key: {}", key);
            return RateLimiter.of("login-" + System.nanoTime(), rateLimiterTemplate.getRateLimiterConfig());
        });

        boolean allowed = limiter.acquirePermission();
        if (!allowed) {
            logger.warn("Rate limit exceeded for: {}", key);
            throw new RateLimitExceededException("Rate limit exceeded for username: " + username + " from IP: " + clientIp);
        }

        logger.debug("Rate limit check passed for: {}", key);
    }

    private String createKey(String username, String clientIp) {
        return username + "|" + clientIp;
    }
}
