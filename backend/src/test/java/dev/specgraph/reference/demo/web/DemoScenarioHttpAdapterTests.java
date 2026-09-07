package dev.specgraph.reference.demo.web;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.jayway.jsonpath.JsonPath;
import dev.specgraph.reference.PostgresIntegrationTestSupport;
import dev.specgraph.reference.ReferenceApplication;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
/** Exercises replayed generated scenarios through persistence, HTTP, and the normal analysis path. */
@SpringBootTest(classes = ReferenceApplication.class, properties = "specgraph.demo.scenarios.enabled=true")
@AutoConfigureMockMvc
@Tag("VFY-REPRODUCIBILITY-001")
class DemoScenarioHttpAdapterTests extends PostgresIntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void replayIsIdempotentAndAllFamiliesUseNormalAnalysis() throws Exception {
        String ordinary = generate("42", "ORDINARY_LOCAL", 0);
        UUID ordinaryId = id(ordinary);
        assertThat(generate("42", "ORDINARY_LOCAL", 0)).isEqualTo(ordinary);
        generate("43", "CROSS_BORDER_GROWTH", 1);
        UUID mixedId = id(generate("44", "MIXED_RED_FLAGS", 4));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions WHERE customer_id = ?", Integer.class, ordinaryId)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM generated_scenarios", Integer.class)).isEqualTo(3);
        mvc.perform(post("/api/customers/{id}/analyses", ordinaryId)).andExpect(status().isCreated()).andExpect(jsonPath("$.riskLevel").value("LOW"));
        mvc.perform(post("/api/customers/{id}/analyses", mixedId)).andExpect(status().isCreated()).andExpect(jsonPath("$.riskLevel").value("HIGH"));
    }

    private String generate(String seed, String family, int risks) throws Exception {
        return mvc.perform(post("/api/demo/scenarios").contentType(MediaType.APPLICATION_JSON)
                .content("{\"seed\":\"" + seed + "\",\"family\":\"" + family + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.seed").value(seed))
                .andExpect(jsonPath("$.family").value(family)).andExpect(jsonPath("$.riskEvidenceCount").value(risks))
                .andReturn().getResponse().getContentAsString();
    }
    private static UUID id(String response) { return UUID.fromString(JsonPath.read(response, "$.customerId")); }
}
