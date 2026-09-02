package dev.specgraph.reference.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.specgraph.reference.PostgresIntegrationTestSupport;
import dev.specgraph.reference.ReferenceApplication;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@Tag("VFY-ANALYSIS-001")
@Tag("VFY-ANALYSIS-HISTORY-001")
@SpringBootTest(classes = ReferenceApplication.class)
@AutoConfigureMockMvc
class AnalysisAcceptanceTests extends PostgresIntegrationTestSupport {
    private static final UUID SEEDED_CUSTOMER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID STABLE_CUSTOMER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID HIGH_RISK_CUSTOMER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID UNKNOWN_CUSTOMER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void clearAnalysisHistory() {
        jdbc.update("DELETE FROM analysis_history");
    }

    @Test
    void completedAnalysisIsStructuredAttributedPersistedAndInspectable() throws Exception {
        mvc.perform(post("/api/customers/{customerId}/analyses", SEEDED_CUSTOMER_ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value(SEEDED_CUSTOMER_ID.toString()))
                .andExpect(jsonPath("$.operatorId").value("r3-demo-operator"))
                .andExpect(jsonPath("$.riskLevel").value("MEDIUM"))
                .andExpect(jsonPath("$.findingsSummary").isNotEmpty())
                .andExpect(jsonPath("$.recommendations", hasSize(2)))
                .andExpect(jsonPath("$.evidenceProvenance[0].sourceIdentity")
                        .value("synthetic-policy:r3-review-baseline"))
                .andExpect(jsonPath("$.detectorProvenance", hasSize(0)))
                .andExpect(jsonPath("$.modelProvenance.backendIdentity").value("deterministic"))
                .andExpect(jsonPath("$.modelProvenance.modelIdentity").value("r3-offline-baseline-v1"));

        UUID analysisId = jdbc.queryForObject(
                "SELECT analysis_id FROM analysis_history WHERE customer_id = ?",
                UUID.class,
                SEEDED_CUSTOMER_ID);
        assertThat(jdbc.queryForObject(
                        "SELECT detector_provenance::text FROM analysis_history WHERE analysis_id = ?",
                        String.class,
                        analysisId))
                .isEqualTo("[]");
        assertThat(jdbc.queryForObject(
                        "SELECT model_provenance ->> 'backendIdentity' FROM analysis_history WHERE analysis_id = ?",
                        String.class,
                        analysisId))
                .isEqualTo("deterministic");
        assertThat(jdbc.queryForObject(
                        "SELECT model_provenance ->> 'modelIdentity' FROM analysis_history WHERE analysis_id = ?",
                        String.class,
                        analysisId))
                .isEqualTo("r3-offline-baseline-v1");

        mvc.perform(get("/api/customers/{customerId}/analyses", SEEDED_CUSTOMER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].analysisId").value(analysisId.toString()))
                .andExpect(jsonPath("$[0].operatorId").value("r3-demo-operator"))
                .andExpect(jsonPath("$[0].riskLevel").value("MEDIUM"))
                .andExpect(jsonPath("$[0].detectorProvenance", hasSize(0)))
                .andExpect(jsonPath("$[0].modelProvenance.backendIdentity").value("deterministic"));

        mvc.perform(get(
                        "/api/customers/{customerId}/analyses/{analysisId}",
                        SEEDED_CUSTOMER_ID,
                        analysisId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisId").value(analysisId.toString()))
                .andExpect(jsonPath("$.recommendations", hasSize(2)))
                .andExpect(jsonPath("$.modelProvenance.modelIdentity").value("r3-offline-baseline-v1"));
    }

    @Test
    void deterministicBaselineSeparatesNoSignalAndDenseSignalScenarios() throws Exception {
        mvc.perform(post("/api/customers/{customerId}/analyses", STABLE_CUSTOMER_ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.riskLevel").value("LOW"));

        mvc.perform(post("/api/customers/{customerId}/analyses", HIGH_RISK_CUSTOMER_ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.riskLevel").value("HIGH"));
    }

    @Test
    void unknownCustomerCannotProduceACompletedAnalysis() throws Exception {
        mvc.perform(post("/api/customers/{customerId}/analyses", UNKNOWN_CUSTOMER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reason").value("CUSTOMER_NOT_FOUND"));

        mvc.perform(get("/api/customers/{customerId}/analyses", UNKNOWN_CUSTOMER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
