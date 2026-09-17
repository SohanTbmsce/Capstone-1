package com.hdfc.jwtauth.services;

import com.hdfc.jwtauth.entity.Policy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class InMemoryPolicyService {

    private final List<Policy> policies = new ArrayList<>();

    public InMemoryPolicyService() {

        policies.add(new Policy(
                1, "POL1001", "Sachin", "LIFE", true));

        policies.add(new Policy(
                2, "POL1002", "Rahul", "HEALTH", true));

        policies.add(new Policy(
                3, "POL1003", "Sahil", "LIFE", true));

        policies.add(new Policy(
                4, "POL1004", "Rohit", "HEALTH", true));

        policies.add(new Policy(
                5, "POL1005", "Amit", "LIFE", true));

        policies.add(new Policy(
                6, "POL1006", "Priya", "LIFE", true));

        policies.add(new Policy(
                7, "POL1007", "Neha", "HEALTH", true));

        policies.add(new Policy(
                8, "POL1008", "Arjun", "LIFE", true));

        policies.add(new Policy(
                9, "POL1009", "Vikas", "HEALTH", true));

        policies.add(new Policy(
                10, "POL1010", "Karan", "LIFE", true));

        policies.add(new Policy(
                11, "POL1011", "Meera", "HEALTH", false));

        policies.add(new Policy(
                12, "POL1012", "Raj", "LIFE", false));
    }

    public List<Policy> getAllPolicies() {
        return policies;
    }

    public long getTotalPolicies() {
        return policies.size();
    }

    public long getActivePolicies() {
        return policies.stream()
                .filter(Policy::active)
                .count();
    }
}