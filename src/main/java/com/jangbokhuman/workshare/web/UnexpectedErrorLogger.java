package com.jangbokhuman.workshare.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.extern.slf4j.Slf4j;

import org.springframework.core.Ordered;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

// Runs last, after every @ExceptionHandler and Spring's own resolvers, so only exceptions that become a 500
// reach it. It logs them while the request is still known, then lets the shared error page render as before.
@Slf4j
@Component
public class UnexpectedErrorLogger implements HandlerExceptionResolver, Ordered {
    @Override
    public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response, Object handler, Exception error) {
        if (error instanceof AccessDeniedException || error instanceof AuthenticationException) {
            return null;
        }
        log.error("처리하지 못한 오류로 500을 응답합니다: {} {}", request.getMethod(), request.getRequestURI(), error);
        return null;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
