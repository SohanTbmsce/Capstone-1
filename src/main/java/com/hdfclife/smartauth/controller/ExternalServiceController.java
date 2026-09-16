package com.hdfclife.smartauth.controller;

import com.hdfclife.smartauth.dto.request.LoginRequest;
import com.hdfclife.smartauth.dto.response.ExternalLoginResponse;
import com.hdfclife.smartauth.resilience.LoginRateLimiter;
import com.hdfclife.smartauth.service.ExternalLoginService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TEMPORARY ENDPOINT FOR TESTING RESILIENCE4J MODULE
 *
 * ExternalServiceController provides a temporary endpoint to test the resilience
 * module before the final /login endpoint is available.
 *
 * This endpoint MUST be removed after integration with AuthService.
 *
 * Flow:
 * 1. Validate request (400 if invalid)
 * 2. Extract client IP
 * 3. Apply rate limiter (429 if exceeded)
 * 4. Call external service with circuit breaker (401 if invalid creds, 503 if service failure)
 *
 * IMPORTANT: This is NOT the final login flow.
 * Final flow: POST /login -> AuthController -> AuthService
 * AuthService will call LoginRateLimiter and ExternalLoginService
 */
@RestController
@RequestMapping("/external-login")
public class ExternalServiceController {

    private static final Logger logger = LoggerFactory.getLogger(ExternalServiceController.class);

    private final LoginRateLimiter loginRateLimiter;
    private final ExternalLoginService externalLoginService;

    public ExternalServiceController(LoginRateLimiter loginRateLimiter,
                                    ExternalLoginService externalLoginService) {
        this.loginRateLimiter = loginRateLimiter;
        this.externalLoginService = externalLoginService;
    }

    /**
     * Temporary test endpoint for resilience module.
     *
     * IMPORTANT: This endpoint is temporary and exists ONLY to test the
     * Resilience4j implementation (rate limiter, circuit breaker, fallback)
     * before the final /login and AuthService are implemented.
     *
     * Possible responses:
     * - 200: Valid credentials, external authentication successful
     * - 400: Invalid request body (missing username/password)
     * - 401: Invalid credentials (wrong password)
     * - 429: Rate limit exceeded for this username + IP
     * - 503: External authentication service unavailable or circuit open
     *
     * @param loginRequest the login request containing username and password
     * @param request the HTTP request (to extract client IP)
     * @return response based on authentication result
     */
    @PostMapping("/test")
    public ResponseEntity<ExternalLoginResponse> testExternalLogin(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request) throws Exception {

        String username = loginRequest.getUsername();
        String clientIp = request.getRemoteAddr();

        logger.info("Login attempt from username: {}, IP: {}", username, clientIp);

        // 1. Apply rate limiter (runs BEFORE credential verification)
        loginRateLimiter.checkRateLimit(username, clientIp);

        // 2. Call external service with circuit breaker protection
        boolean isValid = externalLoginService.validateExternalLogin(
                username,
                loginRequest.getPassword()
        );

        // 3. Return success response
        logger.info("External login successful for user: {}", username);
        return ResponseEntity.ok(new ExternalLoginResponse(
                true,
                username,
                "External authentication successful"
        ));
    }
}
