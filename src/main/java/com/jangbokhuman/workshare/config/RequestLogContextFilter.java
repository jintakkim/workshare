package com.jangbokhuman.workshare.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

// Puts the request and the signed-in account into every log line written while the request runs,
// and writes one access line per request to the access log.
@Component
public class RequestLogContextFilter extends OncePerRequestFilter {
    static final String REQUEST_KEY = "request";
    static final String ACCESS_LOGGER = "workshare.access";
    private static final Logger accessLog = LoggerFactory.getLogger(ACCESS_LOGGER);
    private static final Pattern STATIC_FILE = Pattern.compile(".*\\.(css|js|svg|woff2?|png|jpe?g|ico)$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long startedAt = System.nanoTime();
        MDC.put(REQUEST_KEY, request.getMethod() + " " + request.getRequestURI() + " (" + describeAccount() + ")");
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (isLoggedRequest(request)) {
                long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;
                accessLog.info("{} {} {} {} {}ms", describeAccount(), request.getMethod(), getPathWithQuery(request),
                        response.getStatus(), elapsedMillis);
            }
            MDC.remove(REQUEST_KEY);
        }
    }

    private boolean isLoggedRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/actuator/") && !STATIC_FILE.matcher(path).matches();
    }

    private String getPathWithQuery(HttpServletRequest request) {
        return request.getQueryString() == null
                ? request.getRequestURI()
                : request.getRequestURI() + "?" + request.getQueryString();
    }

    private String describeAccount() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user.isAdmin() ? "관리자" : "직원 ID " + user.getEmployeeId();
        }
        return "로그인 전";
    }
}
