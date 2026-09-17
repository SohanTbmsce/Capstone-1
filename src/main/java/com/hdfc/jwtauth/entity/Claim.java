package com.hdfc.jwtauth.entity;

public record Claim(
        int claimId,
        String claimNumber,
        int policyId,
        String claimant,
        String status
) {
}