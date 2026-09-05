package dev.specgraph.reference;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("architecture")
/** Proves the selected runtime has no parallel JDBC adapter and confines JPA/Hibernate types to adapters. */
final class JpaPersistenceArchitectureTests {
    private static final Path MAIN_SOURCE = Path.of("src/main/java/dev/specgraph/reference");

    @Test
    void runtimePersistenceUsesJpaWithoutJdbcClient() throws IOException {
        List<Path> javaSources;
        try (var paths = Files.walk(MAIN_SOURCE)) {
            javaSources = paths.filter(path -> path.toString().endsWith(".java")).toList();
        }

        for (Path source : javaSources) {
            String content = Files.readString(source);
            assertThat(content)
                    .as("runtime source %s", source)
                    .doesNotContain("org.springframework.jdbc.core.simple.JdbcClient");
        }
    }

    @Test
    void jpaAndHibernateTypesStayInsideOutboundPersistenceAdapters() throws IOException {
        List<Path> javaSources;
        try (var paths = Files.walk(MAIN_SOURCE)) {
            javaSources = paths.filter(path -> path.toString().endsWith(".java")).toList();
        }

        for (Path source : javaSources) {
            String normalized = source.toString().replace('\\', '/');
            if (normalized.contains("/customer/persistence/")
                    || normalized.contains("/analysis/persistence/")) {
                continue;
            }
            String content = Files.readString(source);
            assertThat(content)
                    .as("non-persistence source %s", source)
                    .doesNotContain("jakarta.persistence")
                    .doesNotContain("org.hibernate");
        }
    }
}
