package dev.specgraph.reference.customer.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specgraph.reference.customer.CustomerActivityPort;
import dev.specgraph.reference.customer.CustomerActivityPortContract;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Tag("VFY-CUSTOMER-READ-001")
@Tag("port_contract")
final class JdbcCustomerActivityAdapterContractTests extends CustomerActivityPortContract {
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("specgraph")
            .withUsername("specgraph")
            .withPassword("specgraph");

    private static final JdbcClient JDBC;
    private static final CustomerActivityPort ADAPTER;

    static {
        POSTGRES.start();

        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
        JDBC = JdbcClient.create(dataSource);
        ADAPTER = new JdbcCustomerActivityAdapter(JDBC, "UTC");
    }

    @Override
    protected CustomerActivityPort activityPort() {
        return ADAPTER;
    }

    @Test
    void convertsTimezoneFreeSourceTimestampsUsingConfiguredSourceZone() {
        var zurichAdapter = new JdbcCustomerActivityAdapter(JDBC, "Europe/Zurich");
        var snapshot = zurichAdapter
                .loadSnapshot(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .orElseThrow();

        assertThat(snapshot.activities().getFirst().createdAt())
                .isEqualTo(Instant.parse("2026-08-28T07:15:00Z"));
        assertThat(snapshot.riskEvidence().getFirst().triggeredAt())
                .isEqualTo(Instant.parse("2026-08-28T07:15:01Z"));
    }
}
