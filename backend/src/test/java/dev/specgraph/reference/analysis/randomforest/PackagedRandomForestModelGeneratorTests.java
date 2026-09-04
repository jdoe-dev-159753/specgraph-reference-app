package dev.specgraph.reference.analysis.randomforest;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Explicitly invoked generation tool; ordinary test runs skip all source-tree writes. */
@Tag("tooling")
final class PackagedRandomForestModelGeneratorTests {
    @Test
    void writesPackagedResourcesOnlyWhenExplicitlyRequested() {
        assumeTrue(Boolean.getBoolean("specgraph.generateRandomForestResources"));
        SyntheticRandomForestModelTrainer.writePackagedResources(
                Path.of(System.getProperty("user.dir"), "src", "main", "resources"));
    }
}
