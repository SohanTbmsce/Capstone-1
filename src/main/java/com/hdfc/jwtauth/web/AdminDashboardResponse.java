package com.hdfc.jwtauth.web;
public record AdminDashboardResponse(
        String welcomeMessage,
        int totalUsers,
        int activeUsers,
        int totalPolicies,
        int activePolicies,
        int totalClaims,
        int pendingClaims
) {
}