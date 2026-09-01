package dev.specgraph.reference.customer.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specgraph.reference.customer.CustomerActivityPort;
import dev.specgraph.reference.customer.CustomerActivityPortContract;
import dev.specgraph.reference.risk.RiskEvidence;
import java.time.Instant;
import java.time.LocalDateTime;
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

    private static final UUID CARD_TRANSACTION_ID =
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1");
    private static final UUID CARD_RULE_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID SEEDED_CARD_ASSESSMENT_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID REPEATED_CARD_ASSESSMENT_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000011");

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

    @Test
    void preservesDistinctAssessmentIdentityWhenTheSameRuleAssessesOneTransactionTwice() {
        JDBC.sql("""
                        INSERT INTO risk_assessments(
                            assessment_id, transaction_id, rule_id, triggered_at, score_contribution)
                        VALUES (:assessmentId, :transactionId, :ruleId, :triggeredAt, :scoreContribution)
                        """)
                .param("assessmentId", REPEATED_CARD_ASSESSMENT_ID)
                .param("transactionId", CARD_TRANSACTION_ID)
                .param("ruleId", CARD_RULE_ID)
                .param("triggeredAt", LocalDateTime.parse("2026-08-28T09:16:01"))
                .param("scoreContribution", new java.math.BigDecimal("7.50"))
                .update();

        try {
            var snapshot = ADAPTER.loadSnapshot(SEEDED_CUSTOMER_ID).orElseThrow();
            var repeatedRuleEvidence = snapshot.riskEvidence().stream()
                    .filter(evidence -> evidence.transactionId().equals(CARD_TRANSACTION_ID))
                    .filter(evidence -> evidence.ruleId().equals(CARD_RULE_ID.toString()))
                    .toList();

            assertThat(repeatedRuleEvidence).hasSize(2);
            assertThat(repeatedRuleEvidence)
                    .extracting(RiskEvidence::assessmentId)
                    .containsExactlyInAnyOrder(SEEDED_CARD_ASSESSMENT_ID, REPEATED_CARD_ASSESSMENT_ID);
        } finally {
            JDBC.sql("DELETE FROM risk_assessments WHERE assessment_id = :assessmentId")
                    .param("assessmentId", REPEATED_CARD_ASSESSMENT_ID)
                    .update();
        }
    }
}
