package dev.specgraph.reference;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.modulith.core.ApplicationModules;

@SpringBootTest
class ReferenceApplicationTests extends PostgresIntegrationTestSupport {
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
