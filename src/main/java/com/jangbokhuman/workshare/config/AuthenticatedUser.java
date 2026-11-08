package com.jangbokhuman.workshare.config;

import lombok.Getter;

import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;

import java.io.Serial;

public final class AuthenticatedUser extends User {
    @Serial private static final long serialVersionUID = 1L;

    @Getter private final Long employeeId;

    public AuthenticatedUser(
            Long employeeId, String name, String password, boolean enabled, String role) {
        super(
                name,
                password,
                enabled,
                true,
                true,
                true,
                AuthorityUtils.createAuthorityList("ROLE_" + role));
        this.employeeId = employeeId;
    }
}
