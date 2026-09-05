package dev.specgraph.reference.identity;

import static org.hamcrest.Matchers.hasItems;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@Tag("VFY-AUTH-001")
@SpringBootTest(classes = ReferenceApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("r4-auth")
/**
 * Full filter-chain evidence for protected capabilities, credential rejection and distinct persisted
 * operator attribution. Local demo users exercise application security, not enterprise IAM integration.
 */
class MultiOperatorSecurityIntegrationTests extends PostgresIntegrationTestSupport {
    private static final UUID CUSTOMER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired MockMvc mvc;

    @Test
    void unauthenticatedSessionCannotReachProtectedCustomerAnalysisOrHistoryCapabilities() throws Exception {
        mvc.perform(get("/api/session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.csrf.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.csrf.token").isNotEmpty());

        mvc.perform(get("/api/customers/{id}", CUSTOMER_ID))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/customers/{id}/analyses", CUSTOMER_ID).with(csrf()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/customers/{id}/analyses", CUSTOMER_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidCredentialsAreRejected() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mvc.perform(post("/api/session/login")
                        .session(session)
                        .with(csrf())
                        .param("username", "operator-alpha")
                        .param("password", "wrong-password"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void twoRealOperatorsProduceDistinctPersistedAnalysisAttribution() throws Exception {
        MockHttpSession alpha = login("operator-alpha", "alpha-demo-2026");
        MockHttpSession beta = login("operator-beta", "beta-demo-2026");

        mvc.perform(get("/api/session").session(alpha))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("AUTHENTICATED"))
                .andExpect(jsonPath("$.operatorId").value("operator-alpha"));
        mvc.perform(get("/api/session").session(beta))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("AUTHENTICATED"))
                .andExpect(jsonPath("$.operatorId").value("operator-beta"));

        mvc.perform(post("/api/customers/{id}/analyses", CUSTOMER_ID)
                        .session(alpha)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.operatorId").value("operator-alpha"));

        mvc.perform(post("/api/customers/{id}/analyses", CUSTOMER_ID)
                        .session(beta)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.operatorId").value("operator-beta"));

        mvc.perform(get("/api/customers/{id}/analyses", CUSTOMER_ID).session(alpha))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].operatorId", hasItems("operator-alpha", "operator-beta")));
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/api/session/login")
                        .session(session)
                        .with(csrf())
                        .param("username", username)
                        .param("password", password))
                .andExpect(status().isNoContent());
        return session;
    }
}
