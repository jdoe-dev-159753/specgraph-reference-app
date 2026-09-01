package dev.specgraph.reference.customer;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.specgraph.reference.PostgresIntegrationTestSupport;
import dev.specgraph.reference.ReferenceApplication;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
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

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

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
