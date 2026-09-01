package dev.specgraph.reference.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.specgraph.reference.PostgresIntegrationTestSupport;
import dev.specgraph.reference.ReferenceApplication;
import dev.specgraph.reference.risk.RiskEvidence;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@Tag("VFY-CUSTOMER-READ-001")
@SpringBootTest(classes = ReferenceApplication.class)
@AutoConfigureMockMvc
class CustomerReadAcceptanceTests extends PostgresIntegrationTestSupport {
    private static final UUID SEEDED_CUSTOMER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID GROWING_CROSS_BORDER_CUSTOMER = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID STABLE_CUSTOMER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CARD_RULE_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID CONCURRENT_TRANSACTION_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa91");
    private static final UUID CONCURRENT_ASSESSMENT_ID = UUID.fromString("20000000-0000-0000-0000-000000000091");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired CustomerActivityPort activityPort;

    @Test
    void postgresBackedCustomerPreservesTheR1TypedContractAndSourceRiskEvidence() throws Exception {
        mvc.perform(get("/api/customers/{id}", SEEDED_CUSTOMER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(SEEDED_CUSTOMER_ID.toString()))
                .andExpect(jsonPath("$.activities[*].type", containsInAnyOrder("CARD", "PAYMENT", "CRYPTO")))
                .andExpect(jsonPath("$.activities[0].transactionId").value("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1"))
                .andExpect(jsonPath("$.activities[0].amount").isString())
                .andExpect(jsonPath("$.activities[0].amount").value("248.50"))
                .andExpect(jsonPath("$.activities[0].currency").value("CHF"))
                .andExpect(jsonPath("$.activities[0].status").value("Completed"))
                .andExpect(jsonPath("$.activities[0].createdAt").value("2026-08-28T09:15:00Z"))
                .andExpect(jsonPath("$.activities[0].details.merchantName").value("Alpine Camera"))
                .andExpect(jsonPath("$.activities[0].details.cardPresent").isBoolean())
                .andExpect(jsonPath("$.activities[0].details.cardPresent").value(false))
                .andExpect(jsonPath("$.activities[1].details.receiverBankCountry").value("DE"))
                .andExpect(jsonPath("$.activities[2].details.blockchain").value("Bitcoin"))
                .andExpect(jsonPath("$.riskEvidence[0].transactionId").value("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1"))
                .andExpect(jsonPath("$.riskEvidence[0].ruleId").value("10000000-0000-0000-0000-000000000001"))
                .andExpect(jsonPath("$.riskEvidence[0].ruleName").value("Card not present high value"))
                .andExpect(jsonPath("$.riskEvidence[0].triggeredAt").value("2026-08-28T09:15:01Z"))
                .andExpect(jsonPath("$.riskEvidence[0].scoreContribution").exists())
                .andExpect(jsonPath("$.riskEvidence[1].ruleName").value("New crypto destination"));
    }

    @Test
    void amountAndCurrencyRemainIndependentAndExactFromSqlThroughJson() throws Exception {
        Map<String, Object> persisted = jdbc.queryForMap(
                "select amount, currency from transactions where transaction_id = ?",
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1"));
        assertEquals(new BigDecimal("248.50"), persisted.get("amount"));
        assertEquals("CHF", persisted.get("currency"));

        mvc.perform(get("/api/customers/{id}", SEEDED_CUSTOMER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activities[0].amount").isString())
                .andExpect(jsonPath("$.activities[0].amount").value("248.50"))
                .andExpect(jsonPath("$.activities[0].currency").value("CHF"));

        Integer currencies = jdbc.queryForObject("select count(distinct currency) from transactions", Integer.class);
        assertTrue(currencies != null && currencies >= 4, "fixture must exercise independent multi-currency semantics");
    }

    @Test
    void customerAggregateUsesOnePostgresSnapshotAcrossConcurrentCommit() throws Exception {
        ExecutorService reader = Executors.newSingleThreadExecutor();
        try {
            Future<CustomerSnapshot> snapshotFuture;
            try (Connection writer = dataSource.getConnection()) {
                writer.setAutoCommit(false);
                try (Statement statement = writer.createStatement()) {
                    statement.execute("LOCK TABLE risk_assessments IN ACCESS EXCLUSIVE MODE");
                }
                try (PreparedStatement statement = writer.prepareStatement("""
                        INSERT INTO transactions(
                            transaction_id, customer_id, activity_type, amount, currency, status, created_at)
                        VALUES (?, ?, 'CARD', 19.95, 'CHF', 'Completed', TIMESTAMP '2026-08-31 12:00:00')
                        """)) {
                    statement.setObject(1, CONCURRENT_TRANSACTION_ID);
                    statement.setObject(2, SEEDED_CUSTOMER_ID);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = writer.prepareStatement("""
                        INSERT INTO card_activity(
                            transaction_id, card_pan, card_type, merchant_name, mcc_code,
                            card_present, authorization_code, decline_reason)
                        VALUES (?, '**** **** **** 9091', 'VISA', 'Concurrent Merchant', '5946', false, 'RACE91', NULL)
                        """)) {
                    statement.setObject(1, CONCURRENT_TRANSACTION_ID);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = writer.prepareStatement("""
                        INSERT INTO risk_assessments(
                            assessment_id, transaction_id, rule_id, triggered_at, score_contribution)
                        VALUES (?, ?, ?, TIMESTAMP '2026-08-31 12:00:01', 9.10)
                        """)) {
                    statement.setObject(1, CONCURRENT_ASSESSMENT_ID);
                    statement.setObject(2, CONCURRENT_TRANSACTION_ID);
                    statement.setObject(3, CARD_RULE_ID);
                    statement.executeUpdate();
                }

                snapshotFuture = reader.submit(() -> activityPort.loadSnapshot(SEEDED_CUSTOMER_ID).orElseThrow());
                awaitBlockedRiskEvidenceRead();
                writer.commit();
            }

            CustomerSnapshot snapshot = snapshotFuture.get(5, TimeUnit.SECONDS);
            Set<UUID> activityIds = snapshot.activities().stream()
                    .map(Activity::transactionId)
                    .collect(Collectors.toSet());

            assertThat(activityIds).doesNotContain(CONCURRENT_TRANSACTION_ID);
            assertThat(snapshot.riskEvidence())
                    .extracting(RiskEvidence::transactionId)
                    .doesNotContain(CONCURRENT_TRANSACTION_ID);
            assertThat(snapshot.riskEvidence()).allSatisfy(evidence ->
                    assertThat(activityIds).contains(evidence.transactionId()));
        } finally {
            reader.shutdownNow();
            jdbc.update("DELETE FROM risk_assessments WHERE assessment_id = ?", CONCURRENT_ASSESSMENT_ID);
            jdbc.update("DELETE FROM card_activity WHERE transaction_id = ?", CONCURRENT_TRANSACTION_ID);
            jdbc.update("DELETE FROM transactions WHERE transaction_id = ?", CONCURRENT_TRANSACTION_ID);
        }
    }

    private void awaitBlockedRiskEvidenceRead() throws InterruptedException {
        for (int attempt = 0; attempt < 200; attempt++) {
            Integer waitingReaders = jdbc.queryForObject("""
                    SELECT count(*)
                    FROM pg_locks lock
                    JOIN pg_class relation ON relation.oid = lock.relation
                    WHERE relation.relname = 'risk_assessments'
                      AND lock.mode = 'AccessShareLock'
                      AND NOT lock.granted
                    """, Integer.class);
            if (waitingReaders != null && waitingReaders > 0) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("customer aggregate read never reached the blocked risk-evidence query");
    }

    @Test
    void scenarioCatalogueKeepsNormalCustomersNormalAndShowsTemporalRiskVariation() throws Exception {
        mvc.perform(get("/api/customers/{id}", STABLE_CUSTOMER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riskEvidence").isEmpty())
                .andExpect(jsonPath("$.activities[*].currency", containsInAnyOrder("CHF", "CHF", "CHF")));

        mvc.perform(get("/api/customers/{id}", GROWING_CROSS_BORDER_CUSTOMER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activities[*].currency", hasItems("CHF", "EUR", "ETH")))
                .andExpect(jsonPath("$.riskEvidence[*].ruleName", hasItems(
                        "Growing cross-border payment activity", "New crypto destination")));
    }

    @Test
    void unknownCustomerIsNotFabricated() throws Exception {
        mvc.perform(get("/api/customers/99999999-9999-9999-9999-999999999999"))
                .andExpect(status().isNotFound());
    }
}
