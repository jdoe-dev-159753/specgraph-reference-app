package dev.specgraph.reference.customer;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.specgraph.reference.ReferenceApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = ReferenceApplication.class)
@AutoConfigureMockMvc
class CustomerReadAcceptanceTests {
    @Autowired MockMvc mvc;

    @Test
    void seededCustomerExposesAllActivityFamiliesAndRiskEvidence() throws Exception {
        mvc.perform(get("/api/customers/{id}", SyntheticActivityAdapter.SEEDED_CUSTOMER_ID)
                        .with(httpBasic("operator-a", "demo-a")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(SyntheticActivityAdapter.SEEDED_CUSTOMER_ID.toString()))
                .andExpect(jsonPath("$.activities[*].type",
                        containsInAnyOrder("CARD", "PAYMENT", "CRYPTO")))
                .andExpect(jsonPath("$.activities[0].details.merchantName").value("Alpine Camera"))
                .andExpect(jsonPath("$.activities[1].details.receiverBankCountry").value("DE"))
                .andExpect(jsonPath("$.activities[2].details.blockchain").value("Bitcoin"))
                .andExpect(jsonPath("$.riskEvidence[0].ruleName").value("Card not present high value"));
    }

    @Test
    void secondSeededOperatorCanAuthenticate() throws Exception {
        mvc.perform(get("/api/customers/{id}", SyntheticActivityAdapter.SEEDED_CUSTOMER_ID)
                        .with(httpBasic("operator-b", "demo-b")))
                .andExpect(status().isOk());
    }

    @Test
    void unknownCustomerIsNotFabricated() throws Exception {
        mvc.perform(get("/api/customers/99999999-9999-9999-9999-999999999999")
                        .with(httpBasic("operator-a", "demo-a")))
                .andExpect(status().isNotFound());
    }

    @Test
    void protectedCustomerCapabilityRejectsUnauthenticatedAndInvalidCredentials() throws Exception {
        mvc.perform(get("/api/customers/{id}", SyntheticActivityAdapter.SEEDED_CUSTOMER_ID))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/customers/{id}", SyntheticActivityAdapter.SEEDED_CUSTOMER_ID)
                        .with(httpBasic("operator-a", "wrong")))
                .andExpect(status().isUnauthorized());
    }
}
