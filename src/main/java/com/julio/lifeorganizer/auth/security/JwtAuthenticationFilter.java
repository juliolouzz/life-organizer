package com.julio.lifeorganizer.auth.security;

import com.julio.lifeorganizer.auth.domain.Role;
import com.julio.lifeorganizer.auth.persistence.UserEntity;
import com.julio.lifeorganizer.auth.persistence.UserRepository;
import com.julio.lifeorganizer.auth.service.JwtService;
import com.julio.lifeorganizer.common.exception.AccountDeletionPendingException;
import com.julio.lifeorganizer.common.exception.AuthException;
import com.julio.lifeorganizer.common.exception.InvalidTokenException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
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
    private final UserRepository userRepository;
    private final Clock clock;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   HandlerExceptionResolver resolver,
                                   UserRepository userRepository,
                                   Clock clock) {
        this.jwtService = jwtService;
        this.resolver = resolver;
        this.userRepository = userRepository;
        this.clock = clock;
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

            // Account-state gate: a deletion-pending user must not be allowed to
            // ride out their access token. Exception: /me/restore is the user's
            // own way to undo a delete from the same session, so we let it
            // through and the controller decides. The lookup is skipped when
            // the user row is missing entirely (handled by the controllers as
            // the same observable behaviour as "user not found for token").
            if (!"/api/v1/me/restore".equals(request.getRequestURI())) {
                Optional<UserEntity> maybeUser = userRepository.findById(id);
                if (maybeUser.isPresent()) {
                    UserEntity user = maybeUser.get();
                    if (user.isDeletionPending()
                            && user.getDeletionScheduledAt().isAfter(clock.instant())) {
                        throw new AccountDeletionPendingException(user.getDeletionScheduledAt());
                    }
                    // Slice 12: enforce the revocation epoch. If the user has bumped
                    // token_version since this access token was minted (via password
                    // change, password reset, or sign-out-everywhere), the tv claim
                    // is stale and the request is rejected.
                    jwtService.verifyTokenVersion(claims, user.getTokenVersion());
                }
            }

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
