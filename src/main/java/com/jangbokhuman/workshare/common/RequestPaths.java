package com.jangbokhuman.workshare.common;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;
import java.util.Optional;

public final class RequestPaths {
    private RequestPaths() {
    }

    @SuppressWarnings("unchecked")
    public static Optional<String> findPathVariable(HttpServletRequest request, String name) {
        Map<String, String> variables = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        return variables == null ? Optional.empty() : Optional.ofNullable(variables.get(name));
    }
}
