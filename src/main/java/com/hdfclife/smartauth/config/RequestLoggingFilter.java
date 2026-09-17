package com.hdfclife.smartauth.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(RequestLoggingFilter.class);
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        long startTime = System.currentTimeMillis();
        String method = request.getMethod();
        String uri = request.getRequestURI();
        logger.info("Incoming request: {} , {} " , method , uri);
        try{
            filterChain.doFilter(request, response);
        }
        finally {
            long duration = System.currentTimeMillis() - startTime;
            logger.info(
                    "Outgoing response: {} {} | status={} | duration={}ms",
                    method,
                    uri,
                    response.getStatus(),
                    duration
            );
        }
    }
}
