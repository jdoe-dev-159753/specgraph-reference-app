package dev.specgraph.reference.analysis.randomforest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("VFY-ANALYSIS-CONTRACT-001")
final class RandomForestRiskSignalDetectorRuntimeTests {
    @Test
    void packagedResourcesMatchTheReproducibleGeneratorAndLoadSuccessfully() throws IOException {
        byte[] model = resource(RandomForestRiskSignalDetectorRuntime.MODEL_RESOURCE);
        byte[] manifestBytes = resource(RandomForestRiskSignalDetectorRuntime.MANIFEST_RESOURCE);
        RandomForestModelManifest manifest = RandomForestModelManifest.fromCanonicalProperties(manifestBytes);
        var generated = SyntheticRandomForestModelTrainer.train(
                SyntheticRandomForestModelTrainer.trainingPartition());

        assertThat(model).isEqualTo(generated.protobuf());
        assertThat(manifestBytes).isEqualTo(generated.manifest().toCanonicalProperties());
        assertThat(RandomForestRiskSignalDetectorRuntime.sha256(model)).isEqualTo(manifest.artifactSha256());
        assertThat(RandomForestRiskSignalDetectorRuntime.sha256(manifestBytes))
                .isEqualTo(RandomForestRiskSignalDetectorRuntime.EXPECTED_MANIFEST_SHA256);
        assertThat(manifest.limitation())
                .contains("deterministic serialization sentinel")
                .contains("not an actual training timestamp");
        assertThat(new RandomForestRiskSignalDetectorRuntime().load())
                .isInstanceOf(RandomForestRiskSignalDetectorAdapter.class);
    }

    @Test
    void changedOrMissingResourcesFailClosed() throws IOException {
        byte[] model = resource(RandomForestRiskSignalDetectorRuntime.MODEL_RESOURCE);
        byte[] manifest = resource(RandomForestRiskSignalDetectorRuntime.MANIFEST_RESOURCE);

        byte[] changedManifest = manifest.clone();
        changedManifest[changedManifest.length - 2] ^= 1;
        assertThatThrownBy(() -> runtime(model, changedManifest).load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("trust anchor");

        byte[] changedModel = model.clone();
        changedModel[changedModel.length - 1] ^= 1;
        assertThatThrownBy(() -> runtime(changedModel, manifest).load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("failed validation")
                .hasRootCauseMessage("random-forest model SHA-256 does not match manifest");

        assertThatThrownBy(() -> new RandomForestRiskSignalDetectorRuntime(new ResourceClassLoader(Map.of())).load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing");
    }

    private static RandomForestRiskSignalDetectorRuntime runtime(byte[] model, byte[] manifest) {
        Map<String, byte[]> resources = new HashMap<>();
        resources.put(RandomForestRiskSignalDetectorRuntime.MODEL_RESOURCE, model);
        resources.put(RandomForestRiskSignalDetectorRuntime.MANIFEST_RESOURCE, manifest);
        return new RandomForestRiskSignalDetectorRuntime(new ResourceClassLoader(resources));
    }

    private static byte[] resource(String name) throws IOException {
        try (InputStream input = RandomForestRiskSignalDetectorRuntimeTests.class
                .getClassLoader()
                .getResourceAsStream(name)) {
            assertThat(input).as("classpath resource %s", name).isNotNull();
            return input.readAllBytes();
        }
    }

    private static final class ResourceClassLoader extends ClassLoader {
        private final Map<String, byte[]> resources;

        private ResourceClassLoader(Map<String, byte[]> resources) {
            super(null);
            this.resources = Map.copyOf(resources);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            byte[] bytes = resources.get(name);
            return bytes == null ? null : new ByteArrayInputStream(bytes);
        }
    }
}
