package com.hdfc.jwtauth.controllers;

import com.hdfc.jwtauth.web.ApiResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping
public class DemoController {

    @GetMapping("/public")
    public ApiResponse publicEndpoint() {
        return new ApiResponse("Public endpoint is working");
    }

    @GetMapping("/user/profile")
    public ApiResponse userProfile() {
        return new ApiResponse("USER endpoint: accessible to USER and ADMIN");
    }
//
//    @GetMapping("/admin/dashboard")
//    @PreAuthorize("hasRole('ADMIN')")
//    public ApiResponse adminDashboard() {
//        return new ApiResponse("ADMIN endpoint: accessible only to ADMIN");
//    }
}
