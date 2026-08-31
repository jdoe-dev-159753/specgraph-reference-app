package dev.specgraph.reference.customer;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.specgraph.reference.ReferenceApplication;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@Tag("VFY-CUSTOMER-READ-001")
@SpringBootTest(classes = ReferenceApplication.class)
@AutoConfigureMockMvc
class CustomerReadAcceptanceTests {
    @Autowired MockMvc mvc;

    @Test
    void seededCustomerExposesAllActivityFamiliesAndRiskEvidence() throws Exception {
        mvc.perform(get("/api/customers/{id}", SyntheticActivityAdapter.SEEDED_CUSTOMER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(SyntheticActivityAdapter.SEEDED_CUSTOMER_ID.toString()))
                .andExpect(jsonPath("$.activities[*].type", containsInAnyOrder("CARD", "PAYMENT", "CRYPTO")))
                .andExpect(jsonPath("$.activities[0].transactionId").value("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1"))
                .andExpect(jsonPath("$.activities[0].amount").exists())
                .andExpect(jsonPath("$.activities[0].currency").value("CHF"))
                .andExpect(jsonPath("$.activities[0].status").value("Completed"))
                .andExpect(jsonPath("$.activities[0].createdAt").value("2026-08-28T09:15:00Z"))
                .andExpect(jsonPath("$.activities[0].details.merchantName").value("Alpine Camera"))
                .andExpect(jsonPath("$.activities[1].details.receiverBankCountry").value("DE"))
                .andExpect(jsonPath("$.activities[2].details.blockchain").value("Bitcoin"))
                .andExpect(jsonPath("$.riskEvidence[0].transactionId").value("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1"))
                .andExpect(jsonPath("$.riskEvidence[0].ruleId").value("RULE-CARD-01"))
                .andExpect(jsonPath("$.riskEvidence[0].ruleName").value("Card not present high value"))
                .andExpect(jsonPath("$.riskEvidence[0].triggeredAt").value("2026-08-28T09:15:01Z"))
                .andExpect(jsonPath("$.riskEvidence[0].scoreContribution").exists())
                .andExpect(jsonPath("$.riskEvidence[1].ruleName").value("New crypto destination"));
    }

    @Test
    void unknownCustomerIsNotFabricated() throws Exception {
        mvc.perform(get("/api/customers/99999999-9999-9999-9999-999999999999"))
                .andExpect(status().isNotFound());
    }
}
