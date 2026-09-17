package com.hdfc.jwtauth.web;

public record UserDashboardResponse(
        int policies,
        int claims,
        String profile
) {}