package com.julio.lifeorganizer.auth.security;

import com.julio.lifeorganizer.auth.domain.Role;
import com.julio.lifeorganizer.auth.service.JwtService;
import com.julio.lifeorganizer.common.exception.AuthException;
import com.julio.lifeorganizer.common.exception.InvalidTokenException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

// Validates the Authorization: Bearer <token> header and populates SecurityContext
// with an AuthenticatedUser principal. AuthExceptions thrown during parsing are routed
// through the HandlerExceptionResolver so GlobalExceptionHandler produces the JSON envelope.
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final HandlerExceptionResolver resolver;

    public JwtAuthenticationFilter(JwtService jwtService, HandlerExceptionResolver resolver) {
        this.jwtService = jwtService;
        this.resolver = resolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        try {
            Claims claims = jwtService.parseAccessToken(token);
            Long id = Long.parseLong(claims.getSubject());
            String email = claims.get(JwtService.EMAIL_CLAIM, String.class);
            String role = claims.get(JwtService.ROLE_CLAIM, String.class);

            AuthenticatedUser principal = new AuthenticatedUser(id, email, Role.valueOf(role));
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            List.of(new SimpleGrantedAuthority(role)));
            SecurityContextHolder.getContext().setAuthentication(authentication);

            filterChain.doFilter(request, response);
        } catch (AuthException ex) {
            resolver.resolveException(request, response, null, ex);
        } catch (RuntimeException ex) {
            resolver.resolveException(request, response, null, new InvalidTokenException("Token is invalid"));
        }
    }
}
