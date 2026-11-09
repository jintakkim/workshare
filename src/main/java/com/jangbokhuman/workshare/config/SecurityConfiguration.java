package com.jangbokhuman.workshare.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.time.Duration;
import java.util.LinkedHashMap;

@Configuration
public class SecurityConfiguration {
    @Bean
    PasswordEncoder createPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private AuthenticationEntryPoint createAuthenticationEntryPoint() {
        LinkedHashMap<RequestMatcher, AuthenticationEntryPoint> entries = new LinkedHashMap<>();
        entries.put(new AntPathRequestMatcher("/api/**"), new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED));
        DelegatingAuthenticationEntryPoint entryPoint = new DelegatingAuthenticationEntryPoint(entries);
        entryPoint.setDefaultEntryPoint(new LoginUrlAuthenticationEntryPoint("/login"));
        return entryPoint;
    }

    @Bean
    SecurityFilterChain createSecurityFilterChain(
            HttpSecurity httpSecurity,
            UserDetailsService userDetailsService,
            WorkSettings workSettings,
            @Value("${server.servlet.session.cookie.secure:false}") boolean secureCookies
    ) throws Exception {
        return httpSecurity
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(
                                                "/login",
                                                "/style.css",
                                                "/admin.js",
                                                "/forms.js",
                                                "/favicon.svg",
                                                "/fonts/**",
                                                "/error",
                                                "/actuator/health")
                                        .permitAll()
                                        .requestMatchers("/admin", "/admin/**")
                                        .hasRole("ADMIN")
                                        .requestMatchers(HttpMethod.GET, "/cafeteria-support/schedule", "/leave-calendar")
                                        .hasAnyRole("STAFF", "ADMIN")
                                        .requestMatchers(
                                                "/leave-requests",
                                                "/sharing-shop/time-preference",
                                                "/my-profile",
                                                "/cafeteria-support",
                                                "/cafeteria-support/**")
                                        .hasRole("STAFF")
                                        .anyRequest()
                                        .authenticated())
                .exceptionHandling(
                        errors -> errors.authenticationEntryPoint(createAuthenticationEntryPoint()))
                .formLogin(
                        login ->
                                login.loginPage("/login")
                                        .usernameParameter("name")
                                        .defaultSuccessUrl("/sharing-shop", true)
                                        .permitAll())
                .rememberMe(rememberMe -> rememberMe
                        .userDetailsService(userDetailsService)
                        .key(workSettings.rememberMeKey())
                        .alwaysRemember(true)
                        .tokenValiditySeconds((int) Duration.ofDays(90).toSeconds())
                        .useSecureCookie(secureCookies))
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
                .build();
    }
}
