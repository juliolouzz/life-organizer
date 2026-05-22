package com.julio.lifeorganizer.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.julio.lifeorganizer.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

// Invoked by Spring Security when a protected endpoint is hit without any authentication.
// Writes the 401 envelope directly because at this stage Spring MVC has not yet dispatched,
// so the advice chain cannot run.
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiResponse<Object> body =
                ApiResponse.error("Authentication required", "UNAUTHORIZED");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
