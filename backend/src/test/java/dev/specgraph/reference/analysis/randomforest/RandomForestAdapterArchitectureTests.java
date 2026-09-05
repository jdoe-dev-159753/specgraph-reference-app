package dev.specgraph.reference.analysis.randomforest;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("architecture")
/** Proves ML/statistics library types and offline training remain outside application contracts. */
final class RandomForestAdapterArchitectureTests {
    @Test
    void externalLibraryTypesAndOfflineTrainingStayInsideAdapters() throws IOException {
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
        List<String> commonsMathUsers;
        try (var files = Files.walk(resolvedSourceRoot)) {
            commonsMathUsers = files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> read(path).contains("org.apache.commons.math3"))
                    .map(path -> resolvedSourceRoot.relativize(path).toString().replace('\\', '/'))
                    .toList();
        }
        assertThat(commonsMathUsers).containsExactlyInAnyOrder(
                "dev/specgraph/reference/analysis/BayesianSequentialRiskSignalDetectorAdapter.java",
                "dev/specgraph/reference/analysis/randomforest/RandomForestFeatureDriftDiagnostic.java");
        assertThat(resolvedSourceRoot.resolve(
                        "dev/specgraph/reference/analysis/randomforest/SyntheticRandomForestModelTrainer.java"))
                .doesNotExist();
    }

    @Test
    void publicDriftContractDoesNotExposeTribuoOrCommonsMathTypes() {
        List<String> publicTypes = Stream.of(
                        RandomForestFeatureDriftDiagnostic.class,
                        RandomForestFeatureDriftReport.class)
                .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .flatMap(method -> Stream.concat(
                        Stream.of(method.getGenericReturnType()),
                        Arrays.stream(method.getGenericParameterTypes())))
                .map(java.lang.reflect.Type::getTypeName)
                .toList();

        assertThat(publicTypes).noneMatch(type -> type.contains("org.tribuo")
                || type.contains("org.apache.commons.math3"));
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
