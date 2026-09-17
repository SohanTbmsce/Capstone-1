package com.hdfc.jwtauth.entity;

import java.util.Set;

public record UserAccount(
        String username,
        String password,
        Set<String> roles,
        boolean active)
{}
