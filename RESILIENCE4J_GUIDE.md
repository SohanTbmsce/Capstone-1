# Resilience4j Guide for Smart Authentication Backend

This guide explains the resilience feature in this project from first principles, then connects those ideas to the exact classes implemented in the codebase.

The goal of this module is simple:

> Protect the login flow from abuse and external authentication service failures without mixing resilience logic into authentication business logic.

The current implementation focuses on two Resilience4j patterns:

- Rate limiter: protects the application from too many login attempts.
- Circuit breaker: protects the application from repeatedly calling a failing external authentication service.

The implementation also includes:

- A temporary test controller for exercising the flow.
- A mock external login service.
- Custom exceptions.
- Global exception mapping.
- Unit and controller tests.

## Big Picture

Authentication systems often depend on external systems:

- Identity providers
- LDAP or Active Directory
- OTP services
- Fraud detection services
- Device intelligence services
- Risk scoring APIs

Those systems can be slow, unavailable, overloaded, or partially failing. A login API cannot blindly keep calling a broken dependency forever. If it does, the backend can run out of threads, queues can fill up, latency can spike, and one failing dependency can drag down the entire service.

Resilience is the practice of designing the system so failures are controlled, isolated, and converted into predictable responses.

In this project, resilience is applied to login in two places:

```text
Incoming request
      |
      v
Validate request body
      |
      v
Rate limit by username + IP
      |
      v
Call external auth service through circuit breaker
      |
      v
Return success, invalid credentials, rate limit error, or service unavailable
```

## What Problem Each Pattern Solves

### Rate Limiter

A rate limiter answers this question:

> Is this caller allowed to make another request right now?

For login, this matters because attackers may try:

- Password guessing
- Credential stuffing
- Brute force attacks
- High-frequency API abuse

This project limits attempts using a composite key:

```text
username + "|" + clientIp
```

That means these are treated independently:

- `user1` from `192.168.1.1`
- `user1` from `192.168.1.2`
- `user2` from `192.168.1.1`

The rate limiter runs before password validation. That is intentional. If an attacker sends many bad passwords, the system should reject excessive attempts before doing more expensive authentication work.

### Circuit Breaker

A circuit breaker answers this question:

> Should we call the external service, or should we fail fast because it is unhealthy?

It behaves like an electrical circuit breaker:

- Closed: calls are allowed.
- Open: calls are blocked immediately.
- Half-open: a few test calls are allowed to check recovery.

In this project, the circuit breaker wraps only the external login call. It does not wrap the controller, request validation, rate limiter, JWT creation, or any future token storage logic.

This matters because the circuit breaker should measure dependency health, not user behavior.

## Project Dependencies

The Resilience4j version is declared in `pom.xml`:

```xml
<resilience4j.version>2.1.0</resilience4j.version>
```

The project imports:

```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-core</artifactId>
</dependency>

<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-circuitbreaker</artifactId>
</dependency>

<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-ratelimiter</artifactId>
</dependency>
```

This project uses the programmatic Resilience4j API, not Spring annotations like `@CircuitBreaker` or YAML-based configuration.

That design makes the flow explicit and easier to test in isolation.

## Main Classes

The resilience feature is spread across these files:

```text
src/main/java/com/hdfclife/smartauth/config/ResilienceConfig.java
src/main/java/com/hdfclife/smartauth/resilience/LoginRateLimiter.java
src/main/java/com/hdfclife/smartauth/resilience/LoginCircuitBreaker.java
src/main/java/com/hdfclife/smartauth/resilience/LoginFallbackHandler.java
src/main/java/com/hdfclife/smartauth/service/ExternalLoginService.java
src/main/java/com/hdfclife/smartauth/controller/ExternalServiceController.java
src/main/java/com/hdfclife/smartauth/exception/GlobalExceptionHandler.java
```

Supporting DTOs and exceptions:

```text
src/main/java/com/hdfclife/smartauth/dto/request/LoginRequest.java
src/main/java/com/hdfclife/smartauth/dto/response/ExternalLoginResponse.java
src/main/java/com/hdfclife/smartauth/dto/response/ErrorResponse.java
src/main/java/com/hdfclife/smartauth/exception/RateLimitExceededException.java
src/main/java/com/hdfclife/smartauth/exception/ExternalServiceException.java
src/main/java/com/hdfclife/smartauth/exception/InvalidCredentialsException.java
```

Tests:

```text
src/test/java/com/hdfclife/smartauth/resilience/LoginRateLimiterTest.java
src/test/java/com/hdfclife/smartauth/service/ExternalLoginServiceTest.java
src/test/java/com/hdfclife/smartauth/controller/ExternalServiceControllerTest.java
```

## Request Flow

The temporary endpoint is:

```http
POST /external-login/test
```

The request body is:

```json
{
  "username": "user",
  "password": "password"
}
```

The flow is:

```text
ExternalServiceController.testExternalLogin()
      |
      | 1. Extract username from LoginRequest
      | 2. Extract client IP from HttpServletRequest
      v
LoginRateLimiter.checkRateLimit(username, clientIp)
      |
      | If limit exceeded:
      |   throw RateLimitExceededException
      |   GlobalExceptionHandler returns HTTP 429
      |
      v
ExternalLoginService.validateExternalLogin(username, password)
      |
      v
LoginCircuitBreaker.executeExternalLogin(...)
      |
      v
ExternalLoginService.performExternalLoginValidation(...)
      |
      | If valid:
      |   return true
      |
      | If invalid credentials:
      |   throw InvalidCredentialsException
      |   circuit breaker ignores it
      |   GlobalExceptionHandler returns HTTP 401
      |
      | If external service failure:
      |   throw ExternalServiceException
      |   circuit breaker records it
      |   GlobalExceptionHandler returns HTTP 503
      |
      | If circuit is open:
      |   fail fast
      |   GlobalExceptionHandler returns HTTP 503
```

## HTTP Outcomes

| Scenario | Exception | HTTP Status | Meaning |
| --- | --- | --- | --- |
| Missing username or password | `MethodArgumentNotValidException` | 400 | Bad request |
| Wrong username or password | `InvalidCredentialsException` | 401 | Authentication failed |
| Too many attempts | `RateLimitExceededException` | 429 | Client is sending too many attempts |
| External service unavailable | `ExternalServiceException` | 503 | Dependency is unavailable |
| Circuit breaker open | `ExternalServiceException` or `CallNotPermittedException` | 503 | Calls are temporarily blocked |
| Unexpected bug | `Exception` | 500 | Internal server error |

## ResilienceConfig.java

This class creates the actual Resilience4j objects as Spring beans.

```java
@Configuration
public class ResilienceConfig {
```

`@Configuration` tells Spring that this class defines beans. Those beans are injected into other classes through constructors.

### Circuit Breaker Bean

```java
@Bean
public CircuitBreaker externalLoginCircuitBreaker() {
```

This method creates the circuit breaker used by `LoginCircuitBreaker`.

The configuration:

```java
CircuitBreakerConfig config = CircuitBreakerConfig.custom()
        .failureRateThreshold(50.0f)
        .slowCallRateThreshold(100.0f)
        .waitDurationInOpenState(Duration.ofSeconds(30))
        .slowCallDurationThreshold(Duration.ofSeconds(2))
        .permittedNumberOfCallsInHalfOpenState(3)
        .minimumNumberOfCalls(5)
        .slidingWindowSize(10)
        .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
        .recordExceptions(ExternalServiceException.class)
        .ignoreExceptions(InvalidCredentialsException.class)
        .build();
```

Line by line:

- `failureRateThreshold(50.0f)`: open the circuit when 50 percent or more of measured calls fail.
- `slowCallRateThreshold(100.0f)`: slow calls are tracked, but the threshold is set very high.
- `waitDurationInOpenState(Duration.ofSeconds(30))`: once open, stay open for 30 seconds before trying half-open calls.
- `slowCallDurationThreshold(Duration.ofSeconds(2))`: calls slower than 2 seconds are considered slow calls.
- `permittedNumberOfCallsInHalfOpenState(3)`: when half-open, allow 3 trial calls.
- `minimumNumberOfCalls(5)`: do not calculate failure rate until at least 5 calls are recorded.
- `slidingWindowSize(10)`: measure the last 10 calls.
- `slidingWindowType(COUNT_BASED)`: measure by number of calls, not by time.
- `recordExceptions(ExternalServiceException.class)`: count external service failures as circuit breaker failures.
- `ignoreExceptions(InvalidCredentialsException.class)`: do not count bad passwords as system failures.

The most important design choice is this:

```java
.recordExceptions(ExternalServiceException.class)
.ignoreExceptions(InvalidCredentialsException.class)
```

Invalid credentials are a normal business outcome. They do not mean the external dependency is unhealthy. Counting them as circuit breaker failures would be a major bug because an attacker sending wrong passwords could open the circuit and block valid users.

The circuit breaker is created here:

```java
CircuitBreaker circuitBreaker = CircuitBreaker.of("login-circuit-breaker", config);
```

The name helps identify it in logs, metrics, and debugging.

The event publisher:

```java
circuitBreaker.getEventPublisher()
        .onStateTransition(...)
        .onError(...)
        .onSuccess(...);
```

This adds logging when:

- The circuit changes state.
- A failure is recorded.
- A successful call is recorded.

This is useful because resilience behavior is stateful. Without logs or metrics, it is hard to know why a request was rejected.

### Rate Limiter Bean

```java
@Bean
public RateLimiter loginRateLimiterTemplate() {
```

This method creates a template `RateLimiter`.

The configuration:

```java
RateLimiterConfig config = RateLimiterConfig.custom()
        .limitForPeriod(5)
        .limitRefreshPeriod(Duration.ofMinutes(1))
        .timeoutDuration(Duration.ZERO)
        .build();
```

Line by line:

- `limitForPeriod(5)`: allow 5 requests.
- `limitRefreshPeriod(Duration.ofMinutes(1))`: refresh the allowance every 1 minute.
- `timeoutDuration(Duration.ZERO)`: do not wait for a permit. If the caller is over the limit, fail immediately.

The project uses this bean as a template because each username and IP combination needs its own independent limiter.

## LoginRateLimiter.java

This class owns login attempt rate limiting.

```java
@Component
public class LoginRateLimiter {
```

`@Component` makes it injectable as a Spring bean.

Fields:

```java
private final Map<String, RateLimiter> limiters;
private final RateLimiter rateLimiterTemplate;
```

`limiters` stores one `RateLimiter` per key. The key is `username|clientIp`.

The map is created as:

```java
this.limiters = new ConcurrentHashMap<>();
```

This matters because a web application handles multiple requests concurrently. A normal `HashMap` could corrupt state or behave incorrectly under concurrent access. `ConcurrentHashMap` allows safe concurrent reads and updates.

The main method:

```java
public void checkRateLimit(String username, String clientIp) {
```

This method does not return `true` or `false`. It either completes successfully or throws `RateLimitExceededException`.

That makes usage clean:

```java
loginRateLimiter.checkRateLimit(username, clientIp);
```

If execution continues, the request is allowed.

The key is built here:

```java
String key = createKey(username, clientIp);
```

The limiter is found or created here:

```java
RateLimiter limiter = limiters.computeIfAbsent(key, k -> {
    logger.debug("Creating new rate limiter for key: {}", key);
    return RateLimiter.of("login-" + System.nanoTime(), rateLimiterTemplate.getRateLimiterConfig());
});
```

Why `computeIfAbsent`?

- It creates a limiter only when a key appears for the first time.
- It avoids creating duplicate limiters for the same key under normal concurrent access.
- It keeps the controller and service code simple.

Why use the template config?

```java
rateLimiterTemplate.getRateLimiterConfig()
```

Because all per-key limiters should use the same settings: 5 attempts per minute, fail immediately if exceeded.

The actual permission check:

```java
boolean allowed = limiter.acquirePermission();
```

If `allowed` is `false`, the client has exhausted its permits:

```java
throw new RateLimitExceededException(...);
```

The global exception handler turns that into HTTP 429.

The private helper:

```java
private String createKey(String username, String clientIp) {
    return username + "|" + clientIp;
}
```

This is deliberately simple. For production, the key may need normalization, hashing, tenant ID, trusted proxy handling, and memory cleanup.

## LoginCircuitBreaker.java

This class wraps the external authentication call.

```java
@Component
public class LoginCircuitBreaker {
```

It receives a specific circuit breaker bean:

```java
public LoginCircuitBreaker(@Qualifier("externalLoginCircuitBreaker") CircuitBreaker circuitBreaker) {
```

`@Qualifier` matters because an application can have multiple circuit breakers. This one is specifically for external login.

The main method:

```java
public <T> T executeExternalLogin(CircuitBreakerCallable<T> externalLoginCall) throws Exception {
```

This method is generic. It can return any type `T`, although this project currently returns `Boolean`.

It takes a callback:

```java
CircuitBreakerCallable<T> externalLoginCall
```

That callback is the actual external login work.

The circuit breaker execution:

```java
return circuitBreaker.executeSupplier(() -> {
```

Resilience4j's `executeSupplier` accepts a Java `Supplier`, but `Supplier` cannot throw checked exceptions. That is why the code catches checked exceptions and wraps them:

```java
try {
    return externalLoginCall.call();
} catch (RuntimeException e) {
    throw e;
} catch (Exception e) {
    logger.error("Exception in external login call", e);
    throw new RuntimeException(e);
}
```

The logic here:

- Runtime exceptions are rethrown as-is.
- Checked exceptions are wrapped in `RuntimeException` so they can pass through the supplier API.

When the circuit is open, Resilience4j throws:

```java
CallNotPermittedException
```

This code converts it to the project-specific exception:

```java
throw new ExternalServiceException("External service is currently unavailable due to circuit breaker being open");
```

That keeps the rest of the application from depending directly on Resilience4j exception types.

The unwrap block:

```java
if (e.getCause() instanceof Exception && !(e.getCause() instanceof RuntimeException)) {
    throw (Exception) e.getCause();
}
throw e;
```

This restores checked exceptions that were wrapped earlier.

The state inspection method:

```java
public CircuitBreaker.State getState() {
    return circuitBreaker.getState();
}
```

This is mainly useful for tests and diagnostics.

The reset method:

```java
public void reset() {
    circuitBreaker.reset();
    logger.info("Circuit breaker reset");
}
```

This manually resets the circuit to closed state. It is useful in tests or admin operations, but production code should be careful with manual resets.

The functional interface:

```java
@FunctionalInterface
public interface CircuitBreakerCallable<T> {
    T call() throws Exception;
}
```

This lets callers pass a lambda that can throw checked exceptions:

```java
loginCircuitBreaker.executeExternalLogin(() -> performExternalLoginValidation(username, password));
```

## LoginFallbackHandler.java

This class defines fallback behavior for external service failures.

Important current-code note:

> `LoginFallbackHandler` is implemented and injected into `ExternalLoginService`, but the current `ExternalLoginService.validateExternalLogin` method does not call it. The active fallback behavior is currently handled by `LoginCircuitBreaker`, which converts open-circuit failures into `ExternalServiceException`, and by `GlobalExceptionHandler`, which converts that into HTTP 503.

The intended responsibility of the fallback handler is clear:

```java
public void handleExternalServiceFailure(String username, Throwable throwable)
```

It must not:

- Authenticate the user.
- Return success.
- Generate JWTs.
- Return fake credentials.
- Convert invalid credentials into success.

For login, a fallback must be conservative. If the external authentication service is unavailable, the safe behavior is to reject the login with HTTP 503, not to allow access.

The method checks:

```java
if (throwable instanceof CallNotPermittedException) {
```

That means the circuit breaker is open and did not permit the call.

It then throws:

```java
throw new ExternalServiceException(...)
```

For other failures, it also throws `ExternalServiceException`.

This design is secure because fallback never grants authentication.

## ExternalLoginService.java

This is a temporary mock external authentication service.

The class comment is important:

```java
ExternalLoginService is a TEMPORARY mock of an external authentication service.
```

It exists so the resilience module can be tested before the real login implementation is integrated.

Constants:

```java
private static final String VALID_USERNAME = "user";
private static final String VALID_PASSWORD = "password";
private static final String SERVICE_DOWN_USERNAME = "serviceDown";
```

These are test-only credentials:

- `user/password` succeeds.
- Any other username/password combination fails as invalid credentials.
- `serviceDown` simulates an external service failure.

Injected dependencies:

```java
private final LoginCircuitBreaker loginCircuitBreaker;
private final LoginFallbackHandler loginFallbackHandler;
```

`loginCircuitBreaker` is actively used.

`loginFallbackHandler` is present but not currently invoked.

The public method:

```java
public boolean validateExternalLogin(String username, String password) throws Exception {
    return loginCircuitBreaker.executeExternalLogin(() ->
        performExternalLoginValidation(username, password)
    );
}
```

This means every external validation attempt goes through the circuit breaker.

The internal validation method:

```java
private boolean performExternalLoginValidation(String username, String password) {
```

This method represents the real external service call that would exist later.

Service failure simulation:

```java
if (SERVICE_DOWN_USERNAME.equals(username)) {
    throw new ExternalServiceException("External authentication service is currently unavailable");
}
```

This is counted by the circuit breaker because `ResilienceConfig` records `ExternalServiceException`.

Successful login simulation:

```java
if (VALID_USERNAME.equals(username) && VALID_PASSWORD.equals(password)) {
    return true;
}
```

Invalid credentials:

```java
throw new InvalidCredentialsException("Invalid username or password");
```

This is ignored by the circuit breaker because `ResilienceConfig` ignores `InvalidCredentialsException`.

That distinction is the core of the design.

## ExternalServiceController.java

This controller exposes the temporary test endpoint:

```java
@RestController
@RequestMapping("/external-login")
public class ExternalServiceController {
```

The endpoint:

```java
@PostMapping("/test")
public ResponseEntity<ExternalLoginResponse> testExternalLogin(...)
```

Input validation:

```java
@Valid @RequestBody LoginRequest loginRequest
```

`@Valid` triggers validation annotations in `LoginRequest`.

Client IP:

```java
String clientIp = request.getRemoteAddr();
```

This is good enough for local testing. In production behind a load balancer or reverse proxy, this should use a trusted forwarded header strategy. Blindly trusting `X-Forwarded-For` can be unsafe unless the proxy chain is controlled.

Rate limiting:

```java
loginRateLimiter.checkRateLimit(username, clientIp);
```

This runs before external authentication.

External validation:

```java
boolean isValid = externalLoginService.validateExternalLogin(
        username,
        loginRequest.getPassword()
);
```

The variable `isValid` is assigned but not directly used in a conditional because the service either returns `true` or throws an exception. If it returns, the controller treats it as success.

Success response:

```java
return ResponseEntity.ok(new ExternalLoginResponse(
        true,
        username,
        "External authentication successful"
));
```

All failure responses are centralized in `GlobalExceptionHandler`.

## DTOs

### LoginRequest

```java
@NotBlank(message = "Username cannot be blank")
private String username;

@NotBlank(message = "Password cannot be blank")
private String password;
```

These annotations reject missing or blank username/password values.

If validation fails, Spring throws `MethodArgumentNotValidException`, and the global exception handler returns HTTP 400.

### ExternalLoginResponse

Fields:

```java
private boolean success;
private String username;
private String message;
private Instant timestamp;
```

This is returned for successful external login tests.

### ErrorResponse

Fields:

```java
private int status;
private String message;
private Instant timestamp;
```

This is returned for errors.

The timestamp is assigned in the constructor:

```java
this.timestamp = Instant.now();
```

## Exceptions

The project defines three custom runtime exceptions:

```text
RateLimitExceededException
ExternalServiceException
InvalidCredentialsException
```

Why custom exceptions?

- They make the code readable.
- They decouple application behavior from library-specific exception types.
- They let `GlobalExceptionHandler` map each failure to the correct HTTP status.

### RateLimitExceededException

Means the caller exceeded allowed login attempts.

Mapped to:

```text
HTTP 429 Too Many Requests
```

### ExternalServiceException

Means the external authentication dependency failed or is unavailable.

Mapped to:

```text
HTTP 503 Service Unavailable
```

### InvalidCredentialsException

Means username/password validation failed.

Mapped to:

```text
HTTP 401 Unauthorized
```

Most importantly, this exception is ignored by the circuit breaker.

## GlobalExceptionHandler.java

This class centralizes HTTP error responses.

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
```

`@RestControllerAdvice` applies exception handling across controllers.

Mappings:

```java
@ExceptionHandler(MethodArgumentNotValidException.class)
```

Returns 400 when request body validation fails.

```java
@ExceptionHandler(InvalidCredentialsException.class)
```

Returns 401 for bad credentials.

```java
@ExceptionHandler(RateLimitExceededException.class)
```

Returns 429 for excessive login attempts.

```java
@ExceptionHandler(ExternalServiceException.class)
```

Returns 503 for external auth failures.

```java
@ExceptionHandler(CallNotPermittedException.class)
```

Returns 503 if a raw Resilience4j open-circuit exception reaches the web layer.

```java
@ExceptionHandler(Exception.class)
```

Returns 500 for unexpected errors.

This class is where internal exceptions become API responses.

## Why This Architecture

### Separation of Concerns

Each class has one clear responsibility:

| Class | Responsibility |
| --- | --- |
| `ResilienceConfig` | Build configured Resilience4j objects |
| `LoginRateLimiter` | Decide whether an attempt is allowed |
| `LoginCircuitBreaker` | Protect the external auth call |
| `LoginFallbackHandler` | Define safe failure behavior |
| `ExternalLoginService` | Simulate external authentication |
| `ExternalServiceController` | Expose temporary test endpoint |
| `GlobalExceptionHandler` | Convert exceptions into HTTP responses |

This keeps resilience separate from authentication business logic.

### Why Rate Limiter Before Authentication

If rate limiting happened after password validation, attackers could still force the system to perform expensive authentication work for every request.

Running it first means abusive traffic is rejected early.

### Why Key by Username and IP

Only username-based limiting can accidentally block a real user if attackers spam attempts against that username from many IPs.

Only IP-based limiting can accidentally block many users behind the same NAT, office network, or mobile carrier gateway.

Combining username and IP is a balanced local design:

```text
same username + same IP = shared limit
same username + different IP = independent limit
different username + same IP = independent limit
```

In production, this may be combined with global username limits, IP reputation, device ID, tenant ID, and distributed counters.

### Why Circuit Breaker Only Around External Service

The circuit breaker should represent dependency health.

It should not measure:

- Bad user passwords
- Request validation errors
- Local controller bugs
- Rate limit rejections
- JWT generation behavior

Wrapping too much code would make the circuit breaker react to the wrong signals.

### Why Invalid Credentials Are Ignored

Invalid credentials are not system failures. They are normal authentication results.

If invalid credentials counted as failures:

1. An attacker could send many wrong passwords.
2. The circuit breaker would open.
3. Valid users would be blocked even though the external service is healthy.

That would turn the circuit breaker into a denial-of-service amplifier.

### Why ExternalServiceException Is Recorded

`ExternalServiceException` represents dependency unavailability.

That is exactly what a circuit breaker should observe.

Repeated external failures indicate that continuing to call the dependency may make the system worse.

## Circuit Breaker State Machine

### Closed

Normal state.

Calls are allowed.

The circuit breaker records success and failure outcomes.

If enough calls fail and the failure percentage crosses the threshold, the circuit opens.

### Open

Failure protection state.

Calls are rejected immediately.

The application does not call the external service.

This protects:

- The backend from waiting on doomed calls.
- The external service from additional load.
- Users from long timeouts.

### Half-Open

Recovery test state.

After `waitDurationInOpenState`, the circuit allows a small number of trial calls.

In this project:

```text
permittedNumberOfCallsInHalfOpenState = 3
```

If trial calls succeed, the circuit closes.

If they fail, the circuit opens again.

## Rate Limiter Mechanics

The configured production-like values are:

```text
5 attempts per 1 minute per username + IP
```

With:

```text
timeoutDuration = 0
```

That means the sixth attempt within the same minute fails immediately.

The caller does not wait for a permit.

For login, fail-fast behavior is preferred because waiting would keep request threads busy and would give a poor user experience.

## Tests

### LoginRateLimiterTest

This test uses a smaller limit:

```text
3 requests per minute
```

That makes tests faster and easier to reason about.

It verifies:

- A normal request is allowed.
- The fourth request is rejected.
- Different username/IP combinations have independent limits.
- Same username from different IPs has independent limits.

### ExternalLoginServiceTest

This test creates its own circuit breaker with smaller thresholds:

```text
minimumNumberOfCalls = 2
waitDurationInOpenState = 1 second
slidingWindowSize = 5
```

It verifies:

- Valid credentials return true.
- Invalid password throws `InvalidCredentialsException`.
- Invalid username throws `InvalidCredentialsException`.
- `serviceDown` throws `ExternalServiceException`.
- Invalid credentials do not open the circuit.
- External service failures open or half-open the circuit.
- When the circuit is open, even valid credentials fail fast.

### ExternalServiceControllerTest

This test uses `MockMvc` and mocks:

- `LoginRateLimiter`
- `ExternalLoginService`

It verifies HTTP behavior:

- 200 for valid credentials.
- 400 for missing username.
- 400 for missing password.
- 401 for invalid credentials.
- 429 for rate limit exceeded.
- 503 for external service failure.

## How to Manually Test

Start the app:

```bash
mvn spring-boot:run
```

Valid login:

```bash
curl -X POST http://localhost:8080/external-login/test \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"user\",\"password\":\"password\"}"
```

Invalid credentials:

```bash
curl -X POST http://localhost:8080/external-login/test \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"user\",\"password\":\"wrong\"}"
```

Simulate external service failure:

```bash
curl -X POST http://localhost:8080/external-login/test \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"serviceDown\",\"password\":\"anything\"}"
```

Trigger rate limiting by sending more than 5 requests in one minute with the same username and IP.

Run tests:

```bash
mvn test
```

## Current Limitations

This implementation is good for a capstone/local backend demonstration, but it has production limitations.

### In-Memory Rate Limiters

`LoginRateLimiter` stores limiters in an in-memory `ConcurrentHashMap`.

Implications:

- Limits reset when the application restarts.
- Limits are not shared across multiple backend instances.
- The map can grow forever if many unique username/IP pairs are used.

Production options:

- Redis-backed rate limiting.
- Expiring cache such as Caffeine.
- API gateway rate limiting.
- WAF or identity-provider-level controls.

### Client IP Extraction

The controller uses:

```java
request.getRemoteAddr()
```

This is fine for direct local testing.

Behind proxies, this may show the proxy IP instead of the real client IP.

Production systems need trusted proxy handling for headers like:

```text
X-Forwarded-For
X-Real-IP
Forwarded
```

Only trust those headers if they are set by infrastructure you control.

### Fallback Handler Is Not Wired Into Flow

`LoginFallbackHandler` is implemented, but the current service does not call it.

The flow still returns safe 503 responses because `LoginCircuitBreaker` and `GlobalExceptionHandler` handle failures.

If you want the fallback handler to be the single place for fallback decisions, `ExternalLoginService.validateExternalLogin` can catch external failures and call:

```java
loginFallbackHandler.handleExternalServiceFailure(username, throwable);
```

### Temporary External Login Service

`ExternalLoginService` currently uses hardcoded test credentials.

This is not the final authentication implementation.

The final version should call a real external authentication dependency inside the circuit breaker-protected block.

### No Metrics Endpoint Yet

The circuit breaker logs state transitions, but there is no Actuator/Micrometer metrics integration yet.

Production systems should expose metrics such as:

- Circuit breaker state
- Failure rate
- Slow call rate
- Rate limit rejections
- External service latency

## Interview Questions and Answers

### 1. What is Resilience4j?

Resilience4j is a lightweight Java fault-tolerance library. It provides patterns such as circuit breaker, rate limiter, retry, bulkhead, and time limiter. In this project, it is used to protect the login flow from excessive attempts and repeated external service failures.

### 2. Why did you use a rate limiter in login?

Login endpoints are common attack targets. A rate limiter reduces brute force attempts, credential stuffing, and accidental overload by limiting how many attempts a username/IP pair can make in a time window.

### 3. Why does the rate limiter run before credential validation?

Because excessive traffic should be rejected before expensive authentication work. If rate limiting happened after password validation, attackers could still force the system to call the authentication dependency repeatedly.

### 4. Why use username plus IP as the rate limit key?

It balances two risks. Username-only limits can let attackers lock out a user from anywhere. IP-only limits can punish many users behind the same shared network. Combining username and IP gives independent limits per source and target account pair.

### 5. What is a circuit breaker?

A circuit breaker prevents repeated calls to a failing dependency. When failures cross a threshold, it opens and rejects calls immediately. After a wait period, it moves to half-open and allows limited trial calls. If the dependency recovers, it closes again.

### 6. Why is the circuit breaker only around the external login call?

Because the circuit breaker should measure external dependency health. It should not count local validation, rate limiting, bad passwords, or unrelated controller logic as dependency failures.

### 7. Why are invalid credentials ignored by the circuit breaker?

Invalid credentials are a normal business outcome, not a system failure. Counting them would allow attackers to open the circuit by sending wrong passwords, blocking valid users.

### 8. What exceptions does the circuit breaker record?

It records `ExternalServiceException`, which represents external authentication service failure. It ignores `InvalidCredentialsException`.

### 9. What happens when the circuit is open?

The external service call is not executed. Resilience4j throws `CallNotPermittedException`, which this project converts to `ExternalServiceException`. The global handler returns HTTP 503.

### 10. Why return 503 when the external auth service is down?

Because the user request may be valid, but the server cannot complete authentication due to dependency unavailability. That is a service availability problem, not a credential problem.

### 11. Why return 429 for rate limiting?

HTTP 429 means Too Many Requests. It accurately tells the client that the request was rejected because the caller exceeded allowed request frequency.

### 12. Why return 401 for invalid credentials?

HTTP 401 represents failed authentication. The client provided credentials, but they were not accepted.

### 13. Why use `ConcurrentHashMap` in `LoginRateLimiter`?

Spring Boot handles multiple requests concurrently. The limiter map is shared state. `ConcurrentHashMap` provides thread-safe access when multiple login attempts arrive at the same time.

### 14. What is the weakness of the in-memory rate limiter?

It only works within one application instance. In a horizontally scaled deployment, each instance would have its own counters. It also needs cleanup to avoid unbounded memory growth.

### 15. How would you improve rate limiting for production?

Use a distributed store like Redis, add TTL-based cleanup, include tenant/device dimensions if required, add global username limits, and possibly enforce coarse limits at the API gateway or WAF.

### 16. Why use `timeoutDuration(Duration.ZERO)`?

The login request should fail immediately when the limit is exceeded. Waiting for a permit would tie up request threads and create poor latency.

### 17. What does `minimumNumberOfCalls` do in the circuit breaker?

It prevents the circuit breaker from opening too early based on too little data. In this project, at least 5 calls are needed before failure rate is evaluated.

### 18. What does `slidingWindowSize(10)` mean?

The circuit breaker evaluates the last 10 calls. With a count-based sliding window, old calls fall out as new calls come in.

### 19. What does `failureRateThreshold(50.0f)` mean?

If at least 50 percent of the measured calls fail, and the minimum number of calls has been reached, the circuit opens.

### 20. What is half-open state?

Half-open is a recovery test state. After the open wait duration, the circuit permits a small number of calls. If they succeed, the circuit closes. If they fail, it opens again.

### 21. Why is fallback not allowed to authenticate the user?

Authentication must be strict. If the identity provider is unavailable, the system cannot safely prove the user's identity. A fallback that grants access would create a security vulnerability.

### 22. Why centralize exception handling?

It keeps controllers clean and ensures consistent API responses. Each exception maps to one HTTP status and response shape.

### 23. What is the difference between rate limiter and circuit breaker?

A rate limiter controls caller traffic volume. A circuit breaker controls calls to an unhealthy dependency. Rate limiter protects against too many requests. Circuit breaker protects against repeated dependency failure.

### 24. Would you add retry here?

Very carefully. Retrying login calls can multiply load on an already failing authentication service. If added, retries should be limited, use backoff, and only retry transient failures. Never retry invalid credentials.

### 25. Would you add bulkhead here?

In production, yes. A bulkhead can isolate external auth calls into a limited thread pool or semaphore so they cannot consume all application resources.

### 26. How would this integrate with the final `AuthService`?

The final login flow should call:

```java
loginRateLimiter.checkRateLimit(username, clientIp);
boolean valid = externalLoginService.validateExternalLogin(username, password);
```

Only after successful validation should it generate JWTs, create sessions, or store tokens.

### 27. Should JWT generation be inside the circuit breaker?

No. JWT generation is local application logic. The circuit breaker should wrap only the external dependency call.

### 28. How do tests prove invalid credentials do not open the circuit?

`ExternalLoginServiceTest` repeatedly sends invalid credentials, catches `InvalidCredentialsException`, and then asserts that the circuit breaker state remains `CLOSED`.

### 29. Why does the controller use mocks in tests?

The controller test focuses on HTTP behavior and exception mapping. Mocking the service and rate limiter keeps it from becoming an integration test of all resilience internals.

### 30. What metrics would you monitor?

Monitor rate limit rejections, circuit breaker state, external auth latency, external auth failure rate, HTTP 401/429/503 counts, and login success rate.

## Explaining This Project in an Interview

A strong answer:

> I implemented resilience around the login flow using Resilience4j. The rate limiter runs before credential validation and limits attempts per username and IP to reduce brute force abuse. The circuit breaker wraps only the external authentication call, so it measures dependency health rather than user behavior. External service failures are recorded, but invalid credentials are ignored because bad passwords are normal business outcomes. When the rate limit is exceeded, the API returns 429. When the external service is unavailable or the circuit is open, the API returns 503. The design keeps resilience separate from authentication logic, with centralized exception mapping and focused tests for rate limiting, circuit breaker behavior, and controller responses.

## Final Mental Model

Think of the login flow as three gates:

```text
Gate 1: Is the request valid JSON with username/password?
Gate 2: Is this username/IP allowed to try again now?
Gate 3: Is the external auth service healthy enough to call?
```

Only after all three gates pass should authentication success lead to future application behavior like JWT generation.

The resilience module does not authenticate users by itself. It controls when authentication work is allowed to happen and how failures are safely represented.

