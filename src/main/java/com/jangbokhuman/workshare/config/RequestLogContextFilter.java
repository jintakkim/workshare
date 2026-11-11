package com.jangbokhuman.workshare.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// Puts the request and the signed-in account into every log line written while the request runs.
@Component
public class RequestLogContextFilter extends OncePerRequestFilter {
    static final String REQUEST_KEY = "request";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        MDC.put(REQUEST_KEY, request.getMethod() + " " + request.getRequestURI() + " (" + describeAccount() + ")");
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(REQUEST_KEY);
        }
    }

    private String describeAccount() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user.isAdmin() ? "관리자" : "직원 ID " + user.getEmployeeId();
        }
        return "로그인 전";
    }
}
