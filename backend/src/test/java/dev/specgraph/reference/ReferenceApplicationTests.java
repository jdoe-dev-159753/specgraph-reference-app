package dev.specgraph.reference;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.modulith.core.ApplicationModules;

@SpringBootTest
/**
 * Smoke evidence for application startup plus the controlled four-module Spring Modulith graph.
 * Context loading alone is not treated as functional acceptance.
 */
class ReferenceApplicationTests extends PostgresIntegrationTestSupport {
    /** Proves only that the full Spring context can start against the integration infrastructure. */
    @Test
    void contextLoads() {}

    @Test
    void moduleGraphMatchesTheControlledFourModuleDesign() {
        ApplicationModules modules = ApplicationModules.of(ReferenceApplication.class).verify();

        Set<String> detected = modules.stream()
                .map(module -> module.getIdentifier().toString())
                .collect(Collectors.toSet());

        assertThat(detected).containsExactlyInAnyOrder("identity", "risk", "customer", "analysis");
    }
}
