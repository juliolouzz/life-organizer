package com.julio.lifeorganizer.config;

import com.julio.lifeorganizer.auth.security.JwtAuthenticationEntryPoint;
import com.julio.lifeorganizer.auth.security.JwtAuthenticationFilter;
import com.julio.lifeorganizer.auth.service.JwtService;
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

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            JwtService jwtService,
            JwtAuthenticationEntryPoint entryPoint,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) throws Exception {

        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtService, resolver);

        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(eh -> eh.authenticationEntryPoint(entryPoint))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated())
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
            cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
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
