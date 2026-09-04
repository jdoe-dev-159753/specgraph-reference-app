package dev.specgraph.reference.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specgraph.reference.PostgresIntegrationTestSupport;
import dev.specgraph.reference.ReferenceApplication;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

@Tag("VFY-CONFIDENTIALITY-001")
@Tag("VFY-ANALYSIS-CONTRACT-001")
@SpringBootTest(classes = ReferenceApplication.class)
final class AnalysisBackendSelectionIntegrationTests extends PostgresIntegrationTestSupport {
    @Autowired AnalysisModelPort analysisModel;
    @Autowired ApplicationContext context;
    @Autowired Environment environment;

    @Test
    void defaultStartupUsesDeterministicBackendWithoutMaterializingOpenAiAdapter() {
        assertThat(analysisModel).isInstanceOf(DeterministicAnalysisAdapter.class);
        assertThat(environment.getProperty("specgraph.analysis.backend")).isEqualTo("deterministic");
        assertThat(environment.getProperty("spring.ai.model.chat")).isEqualTo("deterministic");
        assertThat(context.getBeansOfType(SpringAiAnalysisAdapter.class)).isEmpty();
    }
}
