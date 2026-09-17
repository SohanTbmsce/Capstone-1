# JWT Authentication API — Spring Boot + Spring Security

No database. Users and active JWT sessions are stored in memory.

## Requirements
- Java 17+
- Maven 3.9+

## Run
```bash
mvn clean spring-boot:run
```

Server:
`http://localhost:8080`

## Hardcoded users

| Username | Password | Roles |
|---|---|---|
| sachin | sachin123 | USER |
| admin | admin123 | USER, ADMIN |

## APIs

### 1. Login
```bash
curl -X POST http://localhost:8080/login   -H "Content-Type: application/json"   -d '{"username":"sachin","password":"sachin123"}'
```

Copy the `token` from the response.

### 2. Auth
```bash
curl http://localhost:8080/auth   -H "Authorization: Bearer YOUR_TOKEN"
```

### 3. User endpoint
```bash
curl http://localhost:8080/user/profile   -H "Authorization: Bearer YOUR_TOKEN"
```

### 4. Admin endpoint
```bash
curl http://localhost:8080/admin/dashboard   -H "Authorization: Bearer YOUR_TOKEN"
```

A `sachin` token receives 403 here. An `admin` token can access it.

### 5. Logout
```bash
curl -X POST http://localhost:8080/logout   -H "Authorization: Bearer YOUR_TOKEN"
```

After logout, the same JWT is rejected because it has been removed from the in-memory TokenStore.

## Architecture

`Controller -> Spring Security Filter Chain -> JwtAuthenticationFilter -> JwtService + TokenStore`

- JWT is signed with an HMAC secret.
- JWT contains username, roles, issued-at and expiration.
- `TokenStore` is the session/revocation layer.
- Spring Security is stateless.
- Role checks use `hasRole("USER")` / `hasRole("ADMIN")`.
- Global exception handling uses `@RestControllerAdvice`.
- SLF4J + Spring Boot's Logback setup logs requests, responses and errors.

## Important

This is intentionally database-free for the assignment. The in-memory token store is also cleared whenever the application restarts, so previously issued tokens become invalid even if their JWT expiry has not elapsed.
