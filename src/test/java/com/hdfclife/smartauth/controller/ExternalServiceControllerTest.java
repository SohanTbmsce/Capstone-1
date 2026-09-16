package com.hdfclife.smartauth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdfclife.smartauth.dto.request.LoginRequest;
import com.hdfclife.smartauth.exception.ExternalServiceException;
import com.hdfclife.smartauth.exception.InvalidCredentialsException;
import com.hdfclife.smartauth.exception.RateLimitExceededException;
import com.hdfclife.smartauth.resilience.LoginRateLimiter;
import com.hdfclife.smartauth.service.ExternalLoginService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ExternalServiceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LoginRateLimiter loginRateLimiter;

    @MockBean
    private ExternalLoginService externalLoginService;

    @Test
    void testValidCredentials() throws Exception {
        LoginRequest request = new LoginRequest("user", "password");
        when(externalLoginService.validateExternalLogin("user", "password")).thenReturn(true);

        mockMvc.perform(post("/external-login/test")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.username").value("user"));
    }

    @Test
    void testMissingUsername() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setPassword("password");

        mockMvc.perform(post("/external-login/test")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void testMissingPassword() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername("user");

        mockMvc.perform(post("/external-login/test")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void testInvalidCredentials() throws Exception {
        LoginRequest request = new LoginRequest("user", "wrong");
        when(externalLoginService.validateExternalLogin("user", "wrong"))
                .thenThrow(new InvalidCredentialsException("Invalid credentials"));

        mockMvc.perform(post("/external-login/test")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void testRateLimitExceeded() throws Exception {
        LoginRequest request = new LoginRequest("user", "password");
        doThrow(new RateLimitExceededException("Rate limit exceeded"))
                .when(loginRateLimiter).checkRateLimit("user", "127.0.0.1");

        mockMvc.perform(post("/external-login/test")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429));
    }

    @Test
    void testExternalServiceFailure() throws Exception {
        LoginRequest request = new LoginRequest("user", "password");
        when(externalLoginService.validateExternalLogin("user", "password"))
                .thenThrow(new ExternalServiceException("Service unavailable"));

        mockMvc.perform(post("/external-login/test")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }
}
