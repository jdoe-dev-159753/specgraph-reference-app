package dev.specgraph.reference.customer.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specgraph.reference.customer.Activity;
import dev.specgraph.reference.customer.CustomerActivityPort;
import dev.specgraph.reference.customer.CustomerActivityPortContract;
import dev.specgraph.reference.customer.CustomerReviewQuery;
import dev.specgraph.reference.risk.RiskEvidence;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Tag("VFY-CUSTOMER-READ-001")
@Tag("port_contract")
final class JpaCustomerActivityAdapterContractTests extends CustomerActivityPortContract {
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
    private static final UUID DENSE_CUSTOMER_ID =
            UUID.fromString("55555555-5555-5555-5555-555555555555");

    private static final JdbcClient JDBC;
    private static final LocalContainerEntityManagerFactoryBean ENTITY_MANAGER_FACTORY;
    private static final EntityManager ENTITY_MANAGER;
    private static final JpaCustomerActivityAdapter JPA_ADAPTER;
    private static final CustomerActivityPort ADAPTER;

    static {
        POSTGRES.start();

        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
        JDBC = JdbcClient.create(dataSource);

        var vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setGenerateDdl(false);
        vendorAdapter.setDatabasePlatform("org.hibernate.dialect.PostgreSQLDialect");
        ENTITY_MANAGER_FACTORY = new LocalContainerEntityManagerFactoryBean();
        ENTITY_MANAGER_FACTORY.setDataSource(dataSource);
        ENTITY_MANAGER_FACTORY.setJpaVendorAdapter(vendorAdapter);
        ENTITY_MANAGER_FACTORY.setPackagesToScan("dev.specgraph.reference.customer.persistence");
        ENTITY_MANAGER_FACTORY.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none"));
        ENTITY_MANAGER_FACTORY.afterPropertiesSet();
        ENTITY_MANAGER = ENTITY_MANAGER_FACTORY.getObject().createEntityManager();
        JPA_ADAPTER = new JpaCustomerActivityAdapter(ENTITY_MANAGER, "UTC");
        ADAPTER = JPA_ADAPTER;
    }

    @AfterAll
    static void closePersistenceContext() {
        ENTITY_MANAGER.close();
        ENTITY_MANAGER_FACTORY.destroy();
        POSTGRES.stop();
    }

    @Override
    protected CustomerActivityPort activityPort() {
        return ADAPTER;
    }

    @Test
    void convertsTimezoneFreeSourceTimestampsUsingConfiguredSourceZone() {
        var zurichAdapter = new JpaCustomerActivityAdapter(ENTITY_MANAGER, "Europe/Zurich");
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
            ENTITY_MANAGER.clear();
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
            ENTITY_MANAGER.clear();
        }
    }

    @Test
    void pagesAndFiltersDenseCustomerInPostgresqlWithoutLoadingTheCompleteHistory() {
        seedDenseCustomer();
        try {
            ENTITY_MANAGER.clear();
            var firstPage = JPA_ADAPTER
                    .loadReviewPage(DENSE_CUSTOMER_ID, new CustomerReviewQuery(0, 50, null, null, null, null))
                    .orElseThrow();

            assertThat(firstPage.activities()).hasSize(50);
            assertThat(firstPage.totalActivities()).isEqualTo(250);
            assertThat(firstPage.totalRiskEvidence()).isEqualTo(25);
            assertThat(firstPage.totalPages()).isEqualTo(5);
            assertThat(firstPage.hasPrevious()).isFalse();
            assertThat(firstPage.hasNext()).isTrue();
            assertRiskEvidenceBelongsToCurrentPage(firstPage.activities(), firstPage.riskEvidence());

            var completedSecondPage = JPA_ADAPTER
                    .loadReviewPage(DENSE_CUSTOMER_ID, new CustomerReviewQuery(1, 50, null, "Completed", null, null))
                    .orElseThrow();

            assertThat(completedSecondPage.activities()).hasSize(50);
            assertThat(completedSecondPage.activities()).allMatch(activity -> activity.status().equals("Completed"));
            assertThat(completedSecondPage.totalActivities()).isEqualTo(125);
            assertThat(completedSecondPage.totalRiskEvidence()).isEqualTo(25);
            assertThat(completedSecondPage.totalPages()).isEqualTo(3);
            assertThat(completedSecondPage.hasPrevious()).isTrue();
            assertThat(completedSecondPage.hasNext()).isTrue();
            assertRiskEvidenceBelongsToCurrentPage(
                    completedSecondPage.activities(), completedSecondPage.riskEvidence());

            var cardFilter = JPA_ADAPTER
                    .loadReviewPage(
                            SEEDED_CUSTOMER_ID,
                            new CustomerReviewQuery(0, 50, Activity.ActivityType.CARD, null, null, null))
                    .orElseThrow();
            assertThat(cardFilter.activities()).singleElement().satisfies(activity ->
                    assertThat(activity.type()).isEqualTo(Activity.ActivityType.CARD));
            assertThat(cardFilter.riskEvidence()).hasSize(1);
        } finally {
            deleteDenseCustomer();
            ENTITY_MANAGER.clear();
        }
    }

    private void seedDenseCustomer() {
        JDBC.sql("INSERT INTO customers(customer_id) VALUES (:customerId)")
                .param("customerId", DENSE_CUSTOMER_ID)
                .update();
        JDBC.sql("""
                        INSERT INTO transactions(
                            transaction_id, customer_id, activity_type, amount, currency, status, created_at)
                        SELECT
                            ('55555555-5555-5555-5556-' || lpad(gs::text, 12, '0'))::uuid,
                            :customerId,
                            'CARD'::activity_type_enum,
                            100.00 + gs,
                            'CHF',
                            CASE WHEN gs % 2 = 0 THEN 'Completed' ELSE 'Declined' END,
                            TIMESTAMP '2026-01-01 00:00:00' + gs * INTERVAL '1 minute'
                        FROM generate_series(1, 250) AS gs
                        """)
                .param("customerId", DENSE_CUSTOMER_ID)
                .update();
        JDBC.sql("""
                        INSERT INTO card_activity(
                            transaction_id, card_pan, card_type, merchant_name, mcc_code,
                            card_present, authorization_code, decline_reason)
                        SELECT
                            ('55555555-5555-5555-5556-' || lpad(gs::text, 12, '0'))::uuid,
                            '**** **** **** 0000',
                            'VISA',
                            'Dense Fixture Merchant',
                            '5999',
                            true,
                            'DENSE-' || gs,
                            CASE WHEN gs % 2 = 0 THEN NULL ELSE 'Synthetic decline' END
                        FROM generate_series(1, 250) AS gs
                        """).update();
        JDBC.sql("""
                        INSERT INTO risk_assessments(
                            assessment_id, transaction_id, rule_id, triggered_at, score_contribution)
                        SELECT
                            ('66666666-6666-6666-6666-' || lpad(gs::text, 12, '0'))::uuid,
                            ('55555555-5555-5555-5556-' || lpad(gs::text, 12, '0'))::uuid,
                            :ruleId,
                            TIMESTAMP '2026-01-01 00:00:01' + gs * INTERVAL '1 minute',
                            5.00
                        FROM generate_series(10, 250, 10) AS gs
                        """)
                .param("ruleId", CARD_RULE_ID)
                .update();
    }

    private void deleteDenseCustomer() {
        JDBC.sql("""
                        DELETE FROM risk_assessments
                        WHERE transaction_id IN (SELECT transaction_id FROM transactions WHERE customer_id = :customerId)
                        """)
                .param("customerId", DENSE_CUSTOMER_ID)
                .update();
        JDBC.sql("""
                        DELETE FROM card_activity
                        WHERE transaction_id IN (SELECT transaction_id FROM transactions WHERE customer_id = :customerId)
                        """)
                .param("customerId", DENSE_CUSTOMER_ID)
                .update();
        JDBC.sql("DELETE FROM transactions WHERE customer_id = :customerId")
                .param("customerId", DENSE_CUSTOMER_ID)
                .update();
        JDBC.sql("DELETE FROM customers WHERE customer_id = :customerId")
                .param("customerId", DENSE_CUSTOMER_ID)
                .update();
    }

    private static void assertRiskEvidenceBelongsToCurrentPage(
            java.util.List<Activity> activities,
            java.util.List<RiskEvidence> riskEvidence) {
        Set<UUID> pageTransactionIds = activities.stream()
                .map(Activity::transactionId)
                .collect(Collectors.toUnmodifiableSet());
        assertThat(riskEvidence).allMatch(risk -> pageTransactionIds.contains(risk.transactionId()));
    }
}
