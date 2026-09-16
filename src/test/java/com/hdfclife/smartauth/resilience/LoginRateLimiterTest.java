package com.hdfclife.smartauth.resilience;

import com.hdfclife.smartauth.exception.RateLimitExceededException;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class LoginRateLimiterTest {

    private LoginRateLimiter loginRateLimiter;
    private RateLimiter rateLimiterTemplate;

    @BeforeEach
    void setUp() {
        // Create a template with 3 requests per minute for easier testing
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(3)
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .timeoutDuration(Duration.ZERO)
                .build();

        rateLimiterTemplate = RateLimiter.of("test-template", config);
        loginRateLimiter = new LoginRateLimiter(rateLimiterTemplate);
    }

    @Test
    void testAllowedRequest() {
        String username = "user1";
        String clientIp = "192.168.1.1";

        // Should not throw exception
        assertDoesNotThrow(() -> loginRateLimiter.checkRateLimit(username, clientIp));
    }

    @Test
    void testLimitExceeded() {
        String username = "user1";
        String clientIp = "192.168.1.1";

        // Allow 3 requests
        loginRateLimiter.checkRateLimit(username, clientIp);
        loginRateLimiter.checkRateLimit(username, clientIp);
        loginRateLimiter.checkRateLimit(username, clientIp);

        // 4th request should fail
        assertThrows(RateLimitExceededException.class,
                () -> loginRateLimiter.checkRateLimit(username, clientIp));
    }

    @Test
    void testIndependentKeys() {
        String username1 = "user1";
        String username2 = "user2";
        String clientIp1 = "192.168.1.1";
        String clientIp2 = "192.168.1.2";

        // Exhaust rate limit for user1/IP1
        loginRateLimiter.checkRateLimit(username1, clientIp1);
        loginRateLimiter.checkRateLimit(username1, clientIp1);
        loginRateLimiter.checkRateLimit(username1, clientIp1);

        // user1/IP1 should be exhausted
        assertThrows(RateLimitExceededException.class,
                () -> loginRateLimiter.checkRateLimit(username1, clientIp1));

        // But user2/IP2 should still work
        assertDoesNotThrow(() -> loginRateLimiter.checkRateLimit(username2, clientIp2));

        // And user1/IP2 should still work (different IP)
        assertDoesNotThrow(() -> loginRateLimiter.checkRateLimit(username1, clientIp2));
    }

    @Test
    void testSameUserDifferentIP() {
        String username = "user1";

        // Same user, different IPs should have independent limits
        loginRateLimiter.checkRateLimit(username, "192.168.1.1");
        loginRateLimiter.checkRateLimit(username, "192.168.1.1");
        loginRateLimiter.checkRateLimit(username, "192.168.1.1");

        // First IP exhausted, but second IP should work
        assertDoesNotThrow(() -> loginRateLimiter.checkRateLimit(username, "192.168.1.2"));
    }
}
