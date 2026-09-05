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
@SpringBootTest(
        classes = ReferenceApplication.class,
        properties = {
            "specgraph.analysis.backend=openai",
            "spring.ai.openai.api-key=synthetic-test-key-never-used"
        })
/** Proves explicit OPENAI selection projects only the chat model family; network inference is mocked. */
final class OpenAiModelSelectionIntegrationTests extends PostgresIntegrationTestSupport {
    @Autowired AnalysisModelPort analysisModel;
    @Autowired ApplicationContext context;
    @Autowired Environment environment;

    @Test
    void typedBackendSelectionSelectsOpenAiAndProjectsOnlyTheChatModelFamily() {
        assertThat(analysisModel).isInstanceOf(SpringAiAnalysisAdapter.class);
        assertThat(environment.getProperty("specgraph.analysis.backend")).isEqualTo("openai");
        assertThat(environment.getProperty("spring.ai.model.chat")).isEqualTo("openai");
        assertThat(environment.getProperty("spring.ai.model.embedding")).isEqualTo("none");
        assertThat(environment.getProperty("spring.ai.model.image")).isEqualTo("none");
        assertThat(environment.getProperty("spring.ai.model.moderation")).isEqualTo("none");
        assertThat(environment.getProperty("spring.ai.model.audio.speech")).isEqualTo("none");
        assertThat(environment.getProperty("spring.ai.model.audio.transcription")).isEqualTo("none");
        assertThat(context.getBeansOfType(LmStudioAnalysisAdapter.class)).isEmpty();
    }
}
