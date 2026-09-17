package com.hdfc.jwtauth.services;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginAttemptService {

    private static final int MAX_FAILED_ATTEMPTS = 5;

    private static final Duration LOCK_DURATION =
            Duration.ofMinutes(5);

    private final Map<String, LoginAttempt> attempts =
            new ConcurrentHashMap<>();

    public boolean isLocked(String username) {

        LoginAttempt attempt = attempts.get(username);

        if (attempt == null) {
            return false;
        }

        if (!attempt.locked()) {
            return false;
        }

        // Check whether lock has expired
        if (Instant.now().isAfter(attempt.lockUntil())) {
            attempts.remove(username);
            return false;
        }

        return true;
    }

    public void loginFailed(String username) {

        attempts.compute(username, (key, existing) -> {

            if (existing == null) {
                return new LoginAttempt(
                        1,
                        false,
                        null
                );
            }

            int failedAttempts =
                    existing.failedAttempts() + 1;

            if (failedAttempts >= MAX_FAILED_ATTEMPTS) {

                return new LoginAttempt(
                        failedAttempts,
                        true,
                        Instant.now().plus(LOCK_DURATION)
                );
            }

            return new LoginAttempt(
                    failedAttempts,
                    false,
                    null
            );
        });
    }

    public void loginSucceeded(String username) {
        attempts.remove(username);
    }

    public int getFailedAttempts(String username) {

        LoginAttempt attempt = attempts.get(username);

        if (attempt == null) {
            return 0;
        }

        return attempt.failedAttempts();
    }

    private record LoginAttempt(
            int failedAttempts,
            boolean locked,
            Instant lockUntil
    ) {}
}