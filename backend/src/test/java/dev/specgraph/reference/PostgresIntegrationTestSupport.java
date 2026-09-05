package dev.specgraph.reference;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Shared Testcontainers boundary that starts one PostgreSQL/pgvector service and projects its dynamic
 * connection properties into Spring tests. Subclasses still own schema and behavioral assertions.
 */
public abstract class PostgresIntegrationTestSupport {
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("specgraph")
            .withUsername("specgraph")
            .withPassword("specgraph");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
