package com.hdfc.jwtauth.security;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TokenStore {

    private final Map<String, String> activeTokens =
            new ConcurrentHashMap<>();

    public void save(String token, String username) {
        activeTokens.put(token, username);
    }

    public boolean isActive(String token) {
        return activeTokens.containsKey(token);
    }

    public String username(String token) {
        return activeTokens.get(token);
    }

    public void remove(String token) {
        activeTokens.remove(token);
    }
}