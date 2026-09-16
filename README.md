# Smart Authentication Backend - Resilience4j Module

## Purpose

This module implements the Resilience4j integration for the smart authentication backend capstone project.

### Key Components

- **Login Rate Limiter**: Limits login attempts based on username + client IP address
- **Login Circuit Breaker**: Protects external authentication service from cascading failures
- **External Login Fallback**: Gracefully handles external service failures
- **Temporary Mock External Login Endpoint**: `/external-login/test` for testing before final `/login` implementation

## Temporary Endpoint

**IMPORTANT**: `/external-login/test` is a **temporary endpoint** that exists ONLY to test the Resilience4j implementation before the final `/login` endpoint is available.

This endpoint will be removed after integration with `AuthService`.

### Endpoint: POST /external-login/test

#### Request
```json
{
  "username": "user",
  "password": "password"
}
```

#### Responses

| Status | Meaning |
|--------|---------|
| 200 | Valid credentials, external authentication successful |
| 400 | Invalid request body (missing username/password) |
| 401 | Invalid credentials (wrong password) |
| 429 | Rate limit exceeded for this username + IP |
| 503 | External service unavailable or circuit breaker open |

#### Test Credentials

- **Valid**: `username = user`, `password = password`
- **Invalid**: Any other username/password combination returns 401
- **Service Failure**: `username = serviceDown` simulates external service failure (503)

## Architecture

```
POST /external-login/test
        |
        v
LoginRateLimiter (check: username + IP)
    |-- Exceeded --> 429 Too Many Requests
    |
    +-- Allowed
        |
        v
ExternalLoginService
        |
        v
LoginCircuitBreaker (protect external call)
        |
        v
Mock External Login
    /    |      \
  200   401    failure
        |        |
        |        v
        |    fallback
        |        |
        |        v
        |       503
        v
ExternalLoginResponse
```

## Critical Distinctions

### 401 Unauthorized vs 503 Service Unavailable

**401 = Invalid Credentials (Business Logic Failure)**
- Wrong password: `username=user, password=wrong`
- Unknown user
- NOT counted as a circuit breaker failure
- Rate limiter DOES consume a request
- Resilience module treats this as a normal business response

**503 Service Unavailable (System Failure)**
- External service down/unreachable: `username=serviceDown`
- Circuit breaker OPEN
- Counted as a circuit breaker failure
- Returns 503
- Resilience module protects subsequent requests

## Rate Limiter Behavior

- **Limit**: 5 attempts per minute per username + IP combination
- **Key**: `username|IP` (each combination is independent)
- **Enforcement**: Runs BEFORE credential verification
- **Rejection**: Returns HTTP 429

### Example: Rate Limiter

```
User: alice, IP: 192.168.1.1
- Request 1 ✓ (4 remaining)
- Request 2 ✓ (3 remaining)
- Request 3 ✓ (2 remaining)
- Request 4 ✓ (1 remaining)
- Request 5 ✓ (0 remaining)
- Request 6 ✗ (429 Too Many Requests)

Different user/IP: bob, IP: 192.168.1.1
- Request 1 ✓ (independent limit)

Same user, different IP: alice, IP: 192.168.1.2
- Request 1 ✓ (independent limit)
```

## Circuit Breaker Behavior

- **Failure Threshold**: 50% of calls
- **Sliding Window**: Last 10 calls
- **Minimum Calls**: 5 calls before evaluating failure rate
- **Wait Duration**: 30 seconds in OPEN state
- **Half-Open**: Tests 3 calls when transitioning from OPEN
- **Exception Handling**:
  - `ExternalServiceException` = **counted as failure** (increments failure rate)
  - `InvalidCredentialsException` = **ignored** (does NOT count as failure)

### Example: Circuit Breaker State Transitions

```
CLOSED (normal)
  |
  +-- 5+ calls, >50% failures
  |
  v
OPEN (failing)
  |
  +-- 30 seconds elapse
  |
  v
HALF_OPEN (testing)
  |
  +-- 3 calls successful
  |
  v
CLOSED (recovered)
```

## Final Integration Contract

After the final `/login` endpoint is implemented, `AuthService` should use the resilience components like this:

```java
@Service
public class AuthService {
    
    private final LoginRateLimiter loginRateLimiter;
    private final ExternalLoginService externalLoginService;
    
    public LoginResponse authenticate(String username, String password, String clientIp) {
        // 1. Apply rate limiter (runs BEFORE credential verification)
        loginRateLimiter.checkRateLimit(username, clientIp);
        
        // 2. Validate credentials with circuit breaker protection
        externalLoginService.validateExternalLogin(username, password);
        
        // 3. Generate JWT (responsibility of AuthService)
        String jwt = generateJwt(username);
        
        // 4. Store JWT in memory (responsibility of AuthService)
        storeToken(jwt, username);
        
        // 5. Return response
        return new LoginResponse(jwt, "Login successful");
    }
}
```

## Configuration

### Resilience4j Settings

**Rate Limiter**:
- `limitForPeriod`: 5 requests
- `limitRefreshPeriod`: 1 minute
- `timeoutDuration`: 0 (fail immediately)

**Circuit Breaker**:
- `failureRateThreshold`: 50%
- `slidingWindowSize`: 10 calls
- `minimumNumberOfCalls`: 5
- `waitDurationInOpenState`: 30 seconds
- `permittedNumberOfCallsInHalfOpenState`: 3
- `recordExceptions`: `ExternalServiceException`
- `ignoreExceptions`: `InvalidCredentialsException`

Configure these values in `ResilienceConfig.java`.

## Exception Hierarchy

```
RuntimeException
├── RateLimitExceededException (429)
├── InvalidCredentialsException (401)
├── ExternalServiceException (503)
└── (others) → GlobalExceptionHandler
```

## Components

### `LoginRateLimiter`
- Enforces per-key rate limiting
- Key: `username|IP`
- Throws: `RateLimitExceededException`

### `ExternalLoginService`
- Temporary mock of external authentication
- Internally uses `LoginCircuitBreaker`
- Throws: `InvalidCredentialsException` or `ExternalServiceException`

### `LoginCircuitBreaker`
- Wraps external service calls only
- Protects against cascading failures
- Distinguishes `InvalidCredentialsException` (ignored) from `ExternalServiceException` (failure)

### `LoginFallbackHandler`
- Handles circuit breaker failures
- Returns: Always throws `ExternalServiceException` (503)
- NEVER authenticates or returns success

### `GlobalExceptionHandler`
- Centralized exception handling
- Maps exceptions to HTTP status codes
- Provides consistent error responses

## Testing

### Unit Tests

Run all tests:
```bash
mvn clean test
```

Run specific test class:
```bash
mvn test -Dtest=LoginRateLimiterTest
mvn test -Dtest=ExternalLoginServiceTest
mvn test -Dtest=ExternalServiceControllerTest
```

### Test Scenarios

#### Scenario 1: Valid Credentials
```bash
curl -X POST http://localhost:8080/external-login/test \
  -H "Content-Type: application/json" \
  -d '{"username":"user","password":"password"}'
# Response: 200 OK
```

#### Scenario 2: Invalid Password
```bash
curl -X POST http://localhost:8080/external-login/test \
  -H "Content-Type: application/json" \
  -d '{"username":"user","password":"wrong"}'
# Response: 401 Unauthorized
# Note: Circuit breaker failure count NOT incremented
```

#### Scenario 3: Service Failure
```bash
curl -X POST http://localhost:8080/external-login/test \
  -H "Content-Type: application/json" \
  -d '{"username":"serviceDown","password":"anything"}'
# Response: 503 Service Unavailable
# Note: Circuit breaker failure count incremented
```

#### Scenario 4: Rate Limit (5 requests in 1 minute)
```bash
# Execute same request 6 times
# Response 1-5: 200 (if credentials valid) or 401 (if invalid)
# Response 6: 429 Too Many Requests
```

#### Scenario 5: Circuit Open (after repeated failures)
```bash
# Send 6+ serviceDown requests to trigger circuit failure
# Subsequent requests return 503 immediately without calling external service
```

## Known Limitations & Future Work

1. **Rate Limiter**: In-memory only. Does not persist across application restarts.
2. **Token Storage**: Not yet implemented (responsibility of AuthService).
3. **JWT Generation**: Not yet implemented (responsibility of AuthService).
4. **External Service Integration**: Currently a mock. Will be replaced with real service integration.
5. **Distributed Rate Limiting**: Current implementation is single-instance only. For distributed systems, consider Redis-backed rate limiter.

## Important Notes

- **Do NOT log passwords** - This implementation does not log password values
- **Authentication responsibility**: This module handles resilience only. Authentication logic belongs in `AuthService`
- **Clean integration**: Other developers can easily integrate resilience components into `AuthService` without understanding internal implementation details
- **Separation of concerns**: Rate limiter, circuit breaker, and fallback are independent and can be tested separately

## Building & Running

```bash
# Build
mvn clean package

# Run
java -jar target/smart-authentication-backend-1.0.0.jar

# Run with Spring Boot Maven plugin
mvn spring-boot:run
```

## Dependencies

- Spring Boot 3.1.5
- Resilience4j 2.1.0
- Java 17
- JUnit 5
- Mockito
