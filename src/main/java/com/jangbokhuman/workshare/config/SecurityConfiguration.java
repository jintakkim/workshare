package com.jangbokhuman.workshare.config;

import com.jangbokhuman.workshare.employee.LoginNames;
import com.jangbokhuman.workshare.employee.Employee;
import com.jangbokhuman.workshare.employee.EmployeeRepository;

import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;

@Configuration
public class SecurityConfiguration {
    @Bean
    PasswordEncoder createPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService createUserDetailsService(
            EmployeeRepository employeeRepository,
            WorkSettings workSettings,
            PasswordEncoder passwordEncoder,
            Clock clock) {
        String adminHash = passwordEncoder.encode(workSettings.adminPassword());
        return enteredName -> {
            String name = LoginNames.normalize(enteredName);
            if (name.equals(LoginNames.normalize(workSettings.adminUsername()))) {
                return new AuthenticatedUser(null, name, adminHash, true, "ADMIN");
            }
            Employee employee =
                    employeeRepository
                            .findByName(name)
                            .orElseThrow(() -> new UsernameNotFoundException("계정 없음"));
            LocalDate today = LocalDate.ofInstant(clock.instant(), workSettings.zone());
            return new AuthenticatedUser(
                    employee.getId(),
                    employee.getName(),
                    employee.getPasswordHash(),
                    employee.getInactiveFrom() == null
                            || today.isBefore(employee.getInactiveFrom()),
                    "STAFF");
        };
    }

    private org.springframework.security.web.AuthenticationEntryPoint
            createAuthenticationEntryPoint() {
        LinkedHashMap<RequestMatcher, AuthenticationEntryPoint> entries =
                new java.util.LinkedHashMap<
                        org.springframework.security.web.util.matcher.RequestMatcher,
                        org.springframework.security.web.AuthenticationEntryPoint>();
        entries.put(
                new org.springframework.security.web.util.matcher.AntPathRequestMatcher("/api/**"),
                new org.springframework.security.web.authentication.HttpStatusEntryPoint(
                        org.springframework.http.HttpStatus.UNAUTHORIZED));
        DelegatingAuthenticationEntryPoint entryPoint =
                new org.springframework.security.web.authentication
                        .DelegatingAuthenticationEntryPoint(entries);
        entryPoint.setDefaultEntryPoint(
                new org.springframework.security.web.authentication
                        .LoginUrlAuthenticationEntryPoint("/login"));
        return entryPoint;
    }

    @Bean
    SecurityFilterChain createSecurityFilterChain(HttpSecurity httpSecurity) throws Exception {
        return httpSecurity
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(
                                                "/login",
                                                "/style.css",
                                                "/admin.js",
                                                "/forms.js",
                                                "/fonts/**",
                                                "/error")
                                        .permitAll()
                                        .requestMatchers("/admin", "/admin/**")
                                        .hasRole("ADMIN")
                                        .requestMatchers(
                                                "/leave-requests",
                                                "/sharing-shop/time-preference",
                                                "/my-profile",
                                                "/supports",
                                                "/supports/**")
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
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
                .build();
    }
}
