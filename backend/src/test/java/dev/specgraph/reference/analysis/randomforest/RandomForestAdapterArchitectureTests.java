package dev.specgraph.reference.analysis.randomforest;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("architecture")
final class RandomForestAdapterArchitectureTests {
    @Test
    void tribuoTypesAndOfflineTrainingStayOutOfRuntimeApplicationContracts() throws IOException {
        Path sourceRoot = Path.of(System.getProperty("user.dir"), "src", "main", "java");
        if (!Files.isDirectory(sourceRoot)) {
            sourceRoot = Path.of(System.getProperty("user.dir"), "backend", "src", "main", "java");
        }
        Path resolvedSourceRoot = sourceRoot;
        List<String> tribuoUsers;
        try (var files = Files.walk(resolvedSourceRoot)) {
            tribuoUsers = files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> read(path).contains("org.tribuo"))
                    .map(path -> resolvedSourceRoot.relativize(path).toString().replace('\\', '/'))
                    .toList();
        }
        assertThat(tribuoUsers).containsExactly(
                "dev/specgraph/reference/analysis/randomforest/RandomForestRiskSignalDetectorAdapter.java");
        assertThat(resolvedSourceRoot.resolve(
                        "dev/specgraph/reference/analysis/randomforest/SyntheticRandomForestModelTrainer.java"))
                .doesNotExist();
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
