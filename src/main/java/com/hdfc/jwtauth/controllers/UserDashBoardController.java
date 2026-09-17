package com.hdfc.jwtauth.controllers;

import com.hdfc.jwtauth.web.UserDashboardResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
public class UserDashBoardController {

    @GetMapping("/dashboard")
    public ResponseEntity<UserDashboardResponse> getDashboard() {

        return ResponseEntity.ok(
                new UserDashboardResponse(
                        2,
                        2,
                        "Active"
                )
        );
    }
}
