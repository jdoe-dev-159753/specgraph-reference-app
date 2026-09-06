package dev.specgraph.reference.analysis.randomforest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specgraph.reference.customer.Activity;
import dev.specgraph.reference.customer.CustomerSnapshot;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("VFY-ANALYSIS-CONTRACT-001")
/**
 * Characterizes the bounded KS review diagnostic, pinned reference integrity and fail-closed inputs.
 * A triggered status requests human review; these tests do not justify automated retraining.
 */
final class RandomForestFeatureDriftDiagnosticTests {
    private final RandomForestFeatureDriftDiagnostic diagnostic = new RandomForestFeatureDriftDiagnostic();

    @Test
    void packagedReferenceIsCanonicalPinnedAndMatchesTheTrainingFeatureDistribution() throws IOException {
        byte[] referenceBytes = resource(RandomForestFeatureDriftDiagnostic.REFERENCE_RESOURCE);
        RandomForestModelManifest modelManifest = RandomForestModelManifest.fromCanonicalProperties(
                resource(RandomForestRiskSignalDetectorRuntime.MANIFEST_RESOURCE));
        var report = diagnostic.assessFeatures(trainingFeatures());

        assertThat(new String(referenceBytes, StandardCharsets.UTF_8)).doesNotContain("\r").endsWith("\n");
        assertThat(RandomForestRiskSignalDetectorRuntime.sha256(referenceBytes))
                .isEqualTo(RandomForestFeatureDriftDiagnostic.EXPECTED_REFERENCE_SHA256)
                .isEqualTo(report.referenceSha256());
        assertThat(report.modelVersion()).isEqualTo(modelManifest.modelVersion());
        assertThat(report.featureSchemaVersion()).isEqualTo(modelManifest.featureSchemaVersion());
        assertThat(report.referenceDatasetIdentity()).isEqualTo(modelManifest.trainingDatasetIdentity());
        assertThat(report.referenceWindowIdentity()).isEqualTo(modelManifest.splitIdentity());
        assertThat(report.referenceObservationCount()).isEqualTo(12);
        assertThat(report.featureStatistics()).containsOnly(
                Map.entry("activity-volume", 0.0),
                Map.entry("crypto-ratio", 0.0),
                Map.entry("cross-border-payment-ratio", 0.0),
                Map.entry("incomplete-ratio", 0.0));
        assertThat(report.maximumStatistic().orElseThrow()).isEqualTo(0.0);
        assertThat(report.status()).isEqualTo(RandomForestFeatureDriftReport.Status.WITHIN_REVIEW_THRESHOLD);
        assertThat(report.metric()).isEqualTo("two-sample-kolmogorov-smirnov-d");
        assertThat(report.metricLibrary()).isEqualTo("apache-commons-math3-3.6.1");
        assertThat(report.limitation())
                .contains("descriptive input shift only")
                .contains("no concept, performance, calibration, probability, or production AML claim");
    }

    @Test
    void diagnosticIsOrderInvariantAndTheInclusiveReviewThresholdIsReproducible() {
        List<RandomForestRiskFeatures> reversed = new ArrayList<>(trainingFeatures());
        Collections.reverse(reversed);
        assertThat(diagnostic.assessFeatures(reversed)).isEqualTo(diagnostic.assessFeatures(trainingFeatures()));

        var atThreshold = diagnostic.assessFeatures(Collections.nCopies(
                12, new RandomForestRiskFeatures(0.12, 0.30, 0.30, 0.30)));
        assertThat(atThreshold.maximumStatistic().orElseThrow()).isEqualTo(0.50);
        assertThat(atThreshold.reviewThreshold()).isEqualTo(0.50);
        assertThat(atThreshold.status()).isEqualTo(RandomForestFeatureDriftReport.Status.REVIEW_TRIGGERED);
        assertThat(atThreshold.reviewTriggered()).isTrue();

        var fullyShifted = diagnostic.assessFeatures(Collections.nCopies(
                12, new RandomForestRiskFeatures(1.0, 1.0, 1.0, 1.0)));
        assertThat(fullyShifted.featureStatistics().values()).containsOnly(1.0);
        assertThat(fullyShifted.maximumStatistic().orElseThrow()).isEqualTo(1.0);
    }

    @Test
    void fewerThanTwelveObservationsAreReportedAsInsufficientWithoutStatistics() {
        var report = diagnostic.assessFeatures(trainingFeatures().subList(0, 11));

        assertThat(report.status()).isEqualTo(RandomForestFeatureDriftReport.Status.INSUFFICIENT_OBSERVATIONS);
        assertThat(report.observedObservationCount()).isEqualTo(11);
        assertThat(report.minimumObservationCount()).isEqualTo(12);
        assertThat(report.featureStatistics()).isEmpty();
        assertThat(report.maximumStatistic().isEmpty()).isTrue();
        assertThat(report.reviewTriggered()).isFalse();
    }

    @Test
    void publicApiProjectsCustomerSnapshotsWithoutExposingTheStatisticsLibrary() {
        CustomerSnapshot empty = new CustomerSnapshot(UUID.randomUUID(), List.of(), List.of());
        var report = diagnostic.assess(Collections.nCopies(12, empty));

        assertThat(report.observedObservationCount()).isEqualTo(12);
        assertThat(report.featureStatistics())
                .containsKeys(RandomForestRiskFeatures.ORDERED_NAMES.toArray(String[]::new));
    }

    @Test
    void publicApiRejectsUnboundedObservationAndActivityWindows() {
        CustomerSnapshot empty = new CustomerSnapshot(UUID.randomUUID(), List.of(), List.of());
        assertThatThrownBy(() -> diagnostic.assess(Collections.nCopies(
                        RandomForestFeatureDriftDiagnostic.MAX_OBSERVATIONS + 1, empty)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("observation window exceeds");

        Activity activity = new Activity(
                UUID.randomUUID(), Activity.ActivityType.CARD, BigDecimal.ONE, "CHF", "Completed",
                Instant.parse("2026-09-04T00:00:00Z"),
                new Activity.CardDetails("****0000", "VISA", "Synthetic merchant", "0000", true, "AUTH", null));
        CustomerSnapshot oversized = new CustomerSnapshot(
                UUID.randomUUID(),
                Collections.nCopies(RandomForestFeatureDriftDiagnostic.MAX_ACTIVITIES + 1, activity),
                List.of());
        assertThatThrownBy(() -> diagnostic.assess(List.of(oversized)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("activity window exceeds");
    }

    @Test
    void publicReportRejectsCalculatedStatusWithInsufficientObservations() {
        Map<String, Double> statistics = Map.of(
                "activity-volume", 0.0,
                "crypto-ratio", 0.0,
                "cross-border-payment-ratio", 0.0,
                "incomplete-ratio", 0.0);

        assertThatThrownBy(() -> new RandomForestFeatureDriftReport(
                "diagnostic-v1", "model-v1", RandomForestRiskFeatures.SCHEMA_VERSION,
                "reference-dataset-v1", "reference-window-v1", "a".repeat(64),
                12, 11, 12, 0.50, statistics, OptionalDouble.of(0.0),
                RandomForestFeatureDriftReport.Status.WITHIN_REVIEW_THRESHOLD,
                "two-sample-kolmogorov-smirnov-d", "apache-commons-math3-3.6.1",
                "operational review trigger only", "synthetic reference only"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("insufficient observations");
    }

    @Test
    void missingOrChangedReferenceFailsClosed() throws IOException {
        byte[] changed = resource(RandomForestFeatureDriftDiagnostic.REFERENCE_RESOURCE);
        changed[changed.length - 2] ^= 1;

        assertThatThrownBy(() -> new RandomForestFeatureDriftDiagnostic(new ResourceClassLoader(Map.of(
                        RandomForestFeatureDriftDiagnostic.REFERENCE_RESOURCE, changed))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("trust anchor");
        assertThatThrownBy(() -> new RandomForestFeatureDriftDiagnostic(new ResourceClassLoader(Map.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing");
    }

    private static List<RandomForestRiskFeatures> trainingFeatures() {
        return SyntheticRandomForestModelTrainer.trainingPartition().stream()
                .map(SyntheticRandomForestModelTrainer.TrainingRow::features)
                .toList();
    }

    private static byte[] resource(String name) throws IOException {
        try (InputStream input = RandomForestFeatureDriftDiagnosticTests.class
                .getClassLoader().getResourceAsStream(name)) {
            assertThat(input).as("classpath resource %s", name).isNotNull();
            return input.readAllBytes();
        }
    }

    /** Supplies controlled missing or altered classpath resources to exercise fail-closed loading. */
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
