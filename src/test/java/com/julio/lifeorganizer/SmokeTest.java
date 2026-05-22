package com.julio.lifeorganizer;

import javax.sql.DataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

// Phase 0 smoke test: boots the Spring context with a real Postgres backing it.
// Asserts the DataSource is wired - proves the project compiles, configuration resolves,
// and Flyway/JPA do not fail on an empty schema.
@SpringBootTest(classes = LifeOrganizerApplication.class)
@ActiveProfiles("test")
@Testcontainers
@Tag("integration")
class SmokeTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("life_organizer")
                    .withUsername("life_organizer")
                    .withPassword("life_organizer");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }

    @Autowired
    private DataSource dataSource;

    @Test
    void contextLoads_whenAppBootsWithMinimalConfig_succeeds() {
        assertThat(dataSource).isNotNull();
    }
}
