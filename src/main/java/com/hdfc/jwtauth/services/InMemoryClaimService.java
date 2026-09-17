package com.hdfc.jwtauth.services;

import com.hdfc.jwtauth.entity.Claim;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class InMemoryClaimService {

    private final List<Claim> claims = new ArrayList<>();

    public InMemoryClaimService() {

        claims.add(new Claim(
                1, "CLM1001", 1, "Sachin", "PENDING"));

        claims.add(new Claim(
                2, "CLM1002", 2, "Rahul", "PENDING"));

        claims.add(new Claim(
                3, "CLM1003", 3, "Sahil", "PENDING"));

        claims.add(new Claim(
                4, "CLM1004", 4, "Rohit", "PENDING"));

        claims.add(new Claim(
                5, "CLM1005", 5, "Amit", "PENDING"));

        claims.add(new Claim(
                6, "CLM1006", 6, "Priya", "PENDING"));

        claims.add(new Claim(
                7, "CLM1007", 7, "Neha", "PENDING"));

        claims.add(new Claim(
                8, "CLM1008", 8, "Arjun", "PENDING"));

        claims.add(new Claim(
                9, "CLM1009", 9, "Vikas", "PENDING"));

        claims.add(new Claim(
                10, "CLM1010", 10, "Karan", "PENDING"));

        claims.add(new Claim(
                11, "CLM1011", 11, "Meera", "APPROVED"));

        claims.add(new Claim(
                12, "CLM1012", 12, "Raj", "APPROVED"));

        claims.add(new Claim(
                13, "CLM1013", 1, "Sachin", "APPROVED"));

        claims.add(new Claim(
                14, "CLM1014", 2, "Rahul", "REJECTED"));

        claims.add(new Claim(
                15, "CLM1015", 3, "Sahil", "REJECTED"));
    }

    public List<Claim> getAllClaims() {
        return claims;
    }

    public long getTotalClaims() {
        return claims.size();
    }

    public long getPendingClaims() {
        return claims.stream()
                .filter(claim -> claim.status().equals("PENDING"))
                .count();
    }
}