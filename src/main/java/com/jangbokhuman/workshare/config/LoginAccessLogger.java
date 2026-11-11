package com.jangbokhuman.workshare.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

// Login requests are handled inside Spring Security before RequestLogContextFilter runs, so their outcome is logged here.
@Component
public class LoginAccessLogger {
    private static final Logger accessLog = LoggerFactory.getLogger(RequestLogContextFilter.ACCESS_LOGGER);

    @EventListener
    public void logLoginSuccess(InteractiveAuthenticationSuccessEvent event) {
        if (event.getAuthentication().getPrincipal() instanceof AuthenticatedUser user) {
            accessLog.info("{} 로그인 성공", user.isAdmin() ? "관리자" : "직원 ID " + user.getEmployeeId());
        }
    }

    @EventListener
    public void logLoginFailure(AbstractAuthenticationFailureEvent event) {
        accessLog.info("로그인 실패 · 입력한 이름 {}", event.getAuthentication().getName());
    }
}
