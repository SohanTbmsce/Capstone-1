package com.hdfc.jwtauth.services;

import com.hdfc.jwtauth.entity.UserAccount;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class JwtService {

    private static final String SECRET =
            "hdfc-jwt-demo-secret-key-must-be-at-least-32-bytes-long-2026";

    private static final long ACCESS_TOKEN_EXPIRATION_MS =
            30 * 60 * 1000L; // 30 minutes

    private static final long REFRESH_TOKEN_EXPIRATION_MS =
            7 * 24 * 60 * 60 * 1000L; // 7 days

    private final SecretKey key =
            Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    // Generate Access Token
    public String generateAccessToken(UserAccount user) {

        Date now = new Date();
        Date expiry = new Date(
                now.getTime() + ACCESS_TOKEN_EXPIRATION_MS
        );

        return Jwts.builder()
                .subject(user.username())
                .claim("roles", user.roles())
                .claim("type", "access")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    // Generate Refresh Token
    public String generateRefreshToken(UserAccount user) {

        Date now = new Date();
        Date expiry = new Date(
                now.getTime() + REFRESH_TOKEN_EXPIRATION_MS
        );

        return Jwts.builder()
                .subject(user.username())
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    // Parse JWT
    public Claims parse(String token) {

        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String username(String token) {
        return parse(token).getSubject();
    }

    public Set<String> roles(String token) {

        Object value = parse(token).get("roles");

        if (value instanceof java.util.Collection<?> collection) {

            return collection.stream()
                    .map(Object::toString)
                    .collect(Collectors.toSet());
        }

        return Set.of();
    }

    // Check whether token is Access Token
    public boolean isAccessToken(String token) {

        return "access".equals(parse(token).get("type"));
    }

    // Check whether token is Refresh Token
    public boolean isRefreshToken(String token) {

        return "refresh".equals(parse(token).get("type"));
    }
}