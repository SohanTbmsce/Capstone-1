package com.hdfc.jwtauth.services;

import com.hdfc.jwtauth.web.AdminDashboardResponse;
import org.springframework.stereotype.Service;

@Service
public class AdminDashboardService {


        private final InMemoryUserService userService;
        private final InMemoryPolicyService policyService;
        private final InMemoryClaimService claimService;

        public AdminDashboardService(
                InMemoryUserService userService,
                InMemoryPolicyService policyService,
                InMemoryClaimService claimService
        ) {
            this.userService = userService;
            this.policyService = policyService;
            this.claimService = claimService;
        }

        public AdminDashboardResponse getDashboard() {

            int totalUsers = userService.getAllUsers().size();

            int activeUsers = (int) userService.getAllUsers()
                    .stream()
                    .filter(user -> user.active())
                    .count();

            int totalPolicies = (int) policyService.getTotalPolicies();

            int activePolicies = (int) policyService.getActivePolicies();

            int totalClaims = (int) claimService.getTotalClaims();

            int pendingClaims = (int) claimService.getPendingClaims();

            return new AdminDashboardResponse(
                    "Welcome, Admin",
                    totalUsers,
                    activeUsers,
                    totalPolicies,
                    activePolicies,
                    totalClaims,
                    pendingClaims
            );
        }
    }

