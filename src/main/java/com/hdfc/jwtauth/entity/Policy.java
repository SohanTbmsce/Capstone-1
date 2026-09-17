package com.hdfc.jwtauth.entity;


public record Policy(
        int policyId,
        String policyNumber,
        String policyHolder,
        String policyType,
        boolean active
) {
}