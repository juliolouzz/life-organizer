package com.julio.lifeorganizer.config;

import com.julio.lifeorganizer.auth.security.JwtAuthenticationEntryPoint;
import com.julio.lifeorganizer.auth.security.JwtAuthenticationFilter;
import com.julio.lifeorganizer.auth.service.JwtService;
import com.julio.lifeorganizer.common.security.RateLimitFilter;
import com.julio.lifeorganizer.common.security.RateLimiter;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // Comma-separated list of allowed CORS origins. Empty in default + test profiles
    // (no CORS enabled). Local profile sets it to http://localhost:4200 so the
    // Angular dev server can hit the API directly during development.
    @Value("${app.cors.allowed-origins:}")
    private List<String> allowedOrigins;

    // Disabled in the test profile (application-test.yml) so integration tests can
    // register / login arbitrarily many times. Production default is enabled.
    @Value("${app.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            JwtService jwtService,
            JwtAuthenticationEntryPoint entryPoint,
            RateLimiter rateLimiter,
            com.julio.lifeorganizer.auth.persistence.UserRepository userRepository,
            Clock clock,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) throws Exception {

        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(
                jwtService, resolver, userRepository, clock);
        RateLimitFilter rateLimitFilter = new RateLimitFilter(rateLimiter, resolver, rateLimitEnabled);

        http
                // CSRF is intentionally disabled: this is a pure JSON API authenticated
                // with stateless JWTs sent in the Authorization header. Browsers do not
                // automatically attach Authorization headers to cross-site requests, so
                // there is no ambient credential a third-party site could ride on - the
                // precondition for CSRF. Cookies are not used for auth anywhere. If we
                // ever switch to cookie-based sessions, re-enable CSRF protection.
                .csrf(csrf -> csrf.disable()) // lgtm[java/spring-disabled-csrf-protection]
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // no-referrer: prevents the Referer header from leaking URLs that carry
                // sensitive query params (e.g. /reset-password?token=... and
                // /verify-email?token=...) when the user clicks any outbound link.
                .headers(h -> h.referrerPolicy(rp ->
                        rp.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
                .exceptionHandling(eh -> eh.authenticationEntryPoint(entryPoint))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/forgot-password",
                                "/api/v1/auth/reset-password",
                                "/api/v1/auth/verify-email",
                                "/api/v1/auth/resend-verification",
                                "/api/v1/auth/confirm-email-change",
                                "/api/v1/auth/confirm-account-restore").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // OpenAPI / Swagger UI (springdoc). The spec endpoint
                        // and the UI assets stay public so operators can browse
                        // the API without a token; the "Authorize" button on
                        // the UI lets them paste one and try the secured calls.
                        .requestMatchers(
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (allowedOrigins != null && !allowedOrigins.isEmpty() && !allowedOrigins.get(0).isBlank()) {
            // Explicit cross-origin support requested (typically local dev with ng serve on :4200
            // hitting backend on :8080).
            CorsConfiguration cors = new CorsConfiguration();
            cors.setAllowedOrigins(allowedOrigins);
            cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
            cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id"));
            cors.setExposedHeaders(List.of("X-Request-Id"));
            cors.setAllowCredentials(false);
            cors.setMaxAge(3600L);
            source.registerCorsConfiguration("/api/**", cors);
        }
        // No allowed-origins configured -> register no CORS rules. Spring's CorsFilter then
        // treats incoming requests as non-CORS (same-origin) and lets them through. This is the
        // right behaviour for the Docker bundle where nginx proxies /api -> backend so the
        // browser sees one origin. Registering an empty CorsConfiguration would instead reject
        // every request that carries an Origin header (browsers send it even on same-origin
        // JSON POSTs).
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }
}
