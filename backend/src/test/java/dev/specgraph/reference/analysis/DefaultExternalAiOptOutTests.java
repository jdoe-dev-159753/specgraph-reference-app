package dev.specgraph.reference.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specgraph.reference.PostgresIntegrationTestSupport;
import dev.specgraph.reference.ReferenceApplication;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

@Tag("VFY-CONFIDENTIALITY-001")
@SpringBootTest(classes = ReferenceApplication.class)
/** Proves default startup creates no external chat-model family; it does not test provider credentials. */
final class DefaultExternalAiOptOutTests extends PostgresIntegrationTestSupport {
    @Autowired Environment environment;
    @Autowired ApplicationContext applicationContext;

    @Test
    void defaultRuntimeDisablesEveryOpenAiModelFamilyAndCreatesNoChatModel() {
        assertThat(environment.getProperty("specgraph.analysis.backend")).isEqualTo("deterministic");
        assertThat(environment.getProperty("spring.ai.model.chat")).isEqualTo("deterministic");
        assertThat(List.of(
                environment.getProperty("spring.ai.model.embedding"),
                environment.getProperty("spring.ai.model.image"),
                environment.getProperty("spring.ai.model.moderation"),
                environment.getProperty("spring.ai.model.audio.speech"),
                environment.getProperty("spring.ai.model.audio.transcription")))
                .containsOnly("none");

        assertThat(applicationContext.getBeansOfType(ChatModel.class))
                .as("default runtime must not create any external chat model")
                .isEmpty();
    }
}
