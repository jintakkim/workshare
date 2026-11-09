package com.jangbokhuman.workshare.config;

import lombok.Getter;

import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;

public final class AuthenticatedUser extends User {
    private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";

    @Getter
    private final Long employeeId;

    public AuthenticatedUser(Long employeeId, String name, String password, boolean enabled, String role) {
        super(
                name,
                password,
                enabled,
                true,
                true,
                true,
                AuthorityUtils.createAuthorityList("ROLE_" + role)
        );
        this.employeeId = employeeId;
    }

    public boolean isAdmin() {
        return getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(ADMIN_AUTHORITY));
    }
}
