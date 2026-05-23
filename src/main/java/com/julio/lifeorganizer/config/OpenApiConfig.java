package com.julio.lifeorganizer.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger configuration. Surfaces every controller endpoint at
 * <code>/swagger-ui/index.html</code> with a Bearer-token "Authorize" button
 * so an operator can paste a JWT and try the secured endpoints from the
 * browser. The raw spec is available at <code>/v3/api-docs</code>.
 */
@Configuration
public class OpenApiConfig {

    public static final String BEARER_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI lifeOrganizerOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Life Organizer API")
                        .version("v0.12.0")
                        .description("""
                                Personal finance + life management API.

                                All endpoints under /api/v1/ require a JWT access token in the
                                Authorization header (Bearer <token>) unless documented as anonymous
                                (auth, password reset, email verification, account restore, etc.).

                                Use POST /api/v1/auth/login to obtain access + refresh tokens, then
                                paste the access token into the "Authorize" dialog above to try
                                the secured endpoints.""")
                        .contact(new Contact()
                                .name("Julio Louzano")
                                .url("https://github.com/juliolouzz/life-organizer"))
                        .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server().url("/").description("Current host")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME_NAME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste an access token obtained from POST /auth/login")));
    }
}
