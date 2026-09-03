package dev.specgraph.reference.customer;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.specgraph.reference.PostgresIntegrationTestSupport;
import dev.specgraph.reference.ReferenceApplication;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@Tag("VFY-CUSTOMER-READ-001")
@SpringBootTest(classes = ReferenceApplication.class)
@AutoConfigureMockMvc
final class CustomerReviewPaginationAcceptanceTests extends PostgresIntegrationTestSupport {
    private static final UUID SEEDED_CUSTOMER_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired MockMvc mvc;

    @Test
    void defaultRequestRemainsCompatibleButCarriesBoundedPageMetadata() throws Exception {
        mvc.perform(get("/api/customers/{id}", SEEDED_CUSTOMER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activities", hasSize(3)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.pageSize").value(50))
                .andExpect(jsonPath("$.totalActivities").value(3))
                .andExpect(jsonPath("$.totalRiskEvidence").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.hasPrevious").value(false))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void pagesAndFiltersAtTheHttpBoundary() throws Exception {
        mvc.perform(get("/api/customers/{id}", SEEDED_CUSTOMER_ID)
                        .queryParam("pageSize", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activities", hasSize(2)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.pageSize").value(2))
                .andExpect(jsonPath("$.totalActivities").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.hasNext").value(true));

        mvc.perform(get("/api/customers/{id}", SEEDED_CUSTOMER_ID)
                        .queryParam("page", "1")
                        .queryParam("pageSize", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activities", hasSize(1)))
                .andExpect(jsonPath("$.activities[0].type").value("CRYPTO"))
                .andExpect(jsonPath("$.riskEvidence", hasSize(1)))
                .andExpect(jsonPath("$.hasPrevious").value(true))
                .andExpect(jsonPath("$.hasNext").value(false));

        mvc.perform(get("/api/customers/{id}", SEEDED_CUSTOMER_ID)
                        .queryParam("type", "PAYMENT")
                        .queryParam("status", "completed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activities", hasSize(1)))
                .andExpect(jsonPath("$.activities[0].type").value("PAYMENT"))
                .andExpect(jsonPath("$.totalActivities").value(1));
    }

    @Test
    void invalidBoundsFailClosed() throws Exception {
        mvc.perform(get("/api/customers/{id}", SEEDED_CUSTOMER_ID)
                        .queryParam("page", "-1"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/customers/{id}", SEEDED_CUSTOMER_ID)
                        .queryParam("pageSize", "201"))
                .andExpect(status().isBadRequest());
    }
}
