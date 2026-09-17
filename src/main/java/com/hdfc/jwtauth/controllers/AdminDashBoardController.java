package com.hdfc.jwtauth.controllers;

import com.hdfc.jwtauth.services.AdminDashboardService;
import com.hdfc.jwtauth.web.AdminDashboardResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminDashBoardController{
    private final AdminDashboardService dashboardService;

    public AdminDashBoardController(AdminDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }


    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminDashboardResponse> getDashboard() {

        return ResponseEntity.ok(
                dashboardService.getDashboard()
        );
    }
}
