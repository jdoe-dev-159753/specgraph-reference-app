package dev.specgraph.reference.customer.persistence;

import dev.specgraph.reference.customer.CustomerActivityPort;
import dev.specgraph.reference.customer.CustomerActivityPortContract;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
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

    private static final CustomerActivityPort ADAPTER;

    static {
        POSTGRES.start();

        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
        ADAPTER = new JdbcCustomerActivityAdapter(JdbcClient.create(dataSource));
    }

    @Override
    protected CustomerActivityPort activityPort() {
        return ADAPTER;
    }
}
