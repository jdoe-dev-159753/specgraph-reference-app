package dev.specgraph.reference.analysis.randomforest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specgraph.reference.analysis.RiskSignalEvidence;
import dev.specgraph.reference.customer.Activity;
import dev.specgraph.reference.customer.CustomerSnapshot;
import dev.specgraph.reference.risk.RiskEvidence;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("VFY-ANALYSIS-CONTRACT-001")
final class RandomForestRiskSignalDetectorAdapterTests {
    private static final SyntheticRandomForestModelTrainer.GeneratedModel GENERATED =
            SyntheticRandomForestModelTrainer.train(SyntheticRandomForestModelTrainer.trainingPartition());
    private final RandomForestRiskSignalDetectorAdapter detector =
            new RandomForestRiskSignalDetectorAdapter(GENERATED.protobuf(), GENERATED.manifest());

    @Test
    void fixedModelInferenceIsRepeatableBoundedAndSeparatesUnseenGoldenFeatureRows() {
        CustomerSnapshot baseline = snapshot(
                card(1, "Completed", "Merchant A"),
                payment(2, "Completed", "CH", "CH00-A"),
                card(3, "Completed", "Merchant B"),
                payment(4, "Completed", "CH", "CH00-B"));
        CustomerSnapshot elevated = snapshot(
                crypto(5, "Completed", "0x-a"),
                crypto(6, "Declined", "0x-b"),
                payment(7, "Completed", "DE", "DE00-C"),
                card(8, "Declined", "Merchant C"));

        RiskSignalEvidence low = onlySignal(baseline);
        RiskSignalEvidence high = onlySignal(elevated);

        assertThat(low.score()).isBetween(0.0, 1.0);
        assertThat(high.score()).isBetween(0.0, 1.0).isGreaterThan(low.score());
        assertThat(onlySignal(elevated)).isEqualTo(high);
        assertThat(high.detectorIdentity()).isEqualTo("random-forest-review-v1");
        assertThat(high.provenance())
                .containsEntry("detectorFamily", "RANDOM_FOREST")
                .containsEntry("modelSha256", GENERATED.manifest().artifactSha256())
                .containsEntry("trainingPartitionSha256", GENERATED.manifest().trainingPartitionSha256())
                .containsEntry("labelDefinitionIdentity", SyntheticRandomForestModelTrainer.LABEL_DEFINITION_IDENTITY)
                .containsEntry("treeSeed", Long.toString(SyntheticRandomForestModelTrainer.TREE_SEED))
                .containsEntry("inferenceMode", "fixed-protobuf-model; no request-time training")
                .containsEntry("demoLimitation",
                        "hand-assigned synthetic labels separable by construction; no production AML accuracy claim");
    }

    @Test
    void independentlyTrainedFixedModelsProduceTheSameGoldenScores() {
        var second = SyntheticRandomForestModelTrainer.train(
                SyntheticRandomForestModelTrainer.trainingPartition());
        var secondDetector = new RandomForestRiskSignalDetectorAdapter(second.protobuf(), second.manifest());
        CustomerSnapshot scenario = snapshot(
                crypto(10, "Completed", "0x-c"),
                payment(11, "Completed", "GB", "GB00-D"),
                card(12, "Declined", "Merchant D"));

        assertThat(secondDetector.detect(scenario).getFirst().score())
                .isEqualTo(detector.detect(scenario).getFirst().score());
        assertThat(second.manifest().trainingPartitionSha256())
                .isEqualTo(GENERATED.manifest().trainingPartitionSha256());
    }

    @Test
    void featureProjectionIsOrderAndPiiInvariantAndExcludesSourceRiskFromInputs() {
        Activity first = payment(20, "Completed", "DE", "DE00-SECRET-A");
        Activity second = crypto(21, "Declined", "0x-secret-a");
        CustomerSnapshot original = snapshot(first, second);
        CustomerSnapshot reorderedAndRedacted = snapshot(
                crypto(31, "Declined", "0x-redacted"),
                payment(30, "Completed", "DE", "DE00-REDACTED"));
        RiskEvidence sourceRisk = new RiskEvidence(
                UUID.randomUUID(),
                first.transactionId(),
                "SOURCE-RULE",
                "Source-only risk",
                Instant.parse("2026-08-30T09:00:00Z"),
                new BigDecimal("0.99"));
        CustomerSnapshot withSourceRisk = new CustomerSnapshot(
                original.customerId(), original.activities(), List.of(sourceRisk));

        assertThat(RandomForestRiskFeatures.from(original))
                .isEqualTo(RandomForestRiskFeatures.from(reorderedAndRedacted))
                .isEqualTo(RandomForestRiskFeatures.from(withSourceRisk));
        assertThat(RandomForestRiskFeatures.ORDERED_NAMES)
                .containsExactly("activity-volume", "crypto-ratio", "cross-border-payment-ratio", "incomplete-ratio");
        assertThat(onlySignal(withSourceRisk).score()).isEqualTo(onlySignal(original).score());
        assertThat(withSourceRisk.riskEvidence()).containsExactly(sourceRisk);
    }

    @Test
    void modelBytesAndManifestAreValidatedBeforeInference() {
        byte[] tampered = GENERATED.protobuf();
        tampered[tampered.length - 1] ^= 1;
        assertThatThrownBy(() -> new RandomForestRiskSignalDetectorAdapter(tampered, GENERATED.manifest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SHA-256");

        RandomForestModelManifest wrongSchema = copyWithSchema("wrong-schema");
        assertThatThrownBy(() -> new RandomForestRiskSignalDetectorAdapter(GENERATED.protobuf(), wrongSchema))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("feature schema");

        RandomForestModelManifest wrongTreeSeed = copyWithTreeSeed(GENERATED.manifest().treeSeed() + 1);
        assertThatThrownBy(() -> new RandomForestRiskSignalDetectorAdapter(GENERATED.protobuf(), wrongTreeSeed))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("provenance")
                .hasMessageContaining("seed");
    }

    @Test
    void manifestRejectsNonFiniteFeatureSubsampling() {
        var manifest = GENERATED.manifest();

        assertThatThrownBy(() -> new RandomForestModelManifest(
                manifest.modelVersion(), manifest.artifactSha256(), manifest.featureSchemaVersion(),
                manifest.trainingDatasetIdentity(), manifest.trainingPartitionSha256(), manifest.splitIdentity(),
                manifest.trainingSeed(), manifest.treeSeed(), manifest.treeCount(), manifest.maxDepth(),
                Double.NaN, manifest.libraryVersion(), manifest.outputLabels(), manifest.labelDefinitionIdentity(),
                manifest.scoreSemantics(), manifest.limitation()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hyperparameters");
    }

    @Test
    void emptyHistoryProducesNoInventedSignal() {
        assertThat(detector.detect(new CustomerSnapshot(UUID.randomUUID(), List.of(), List.of()))).isEmpty();
    }

    private RandomForestModelManifest copyWithSchema(String schema) {
        var manifest = GENERATED.manifest();
        return new RandomForestModelManifest(
                manifest.modelVersion(), manifest.artifactSha256(), schema,
                manifest.trainingDatasetIdentity(), manifest.trainingPartitionSha256(), manifest.splitIdentity(),
                manifest.trainingSeed(), manifest.treeSeed(), manifest.treeCount(), manifest.maxDepth(),
                manifest.featureSubsampling(), manifest.libraryVersion(), manifest.outputLabels(),
                manifest.labelDefinitionIdentity(),
                manifest.scoreSemantics(), manifest.limitation());
    }

    private RandomForestModelManifest copyWithTreeSeed(long treeSeed) {
        var manifest = GENERATED.manifest();
        return new RandomForestModelManifest(
                manifest.modelVersion(), manifest.artifactSha256(), manifest.featureSchemaVersion(),
                manifest.trainingDatasetIdentity(), manifest.trainingPartitionSha256(), manifest.splitIdentity(),
                manifest.trainingSeed(), treeSeed, manifest.treeCount(), manifest.maxDepth(),
                manifest.featureSubsampling(), manifest.libraryVersion(), manifest.outputLabels(),
                manifest.labelDefinitionIdentity(), manifest.scoreSemantics(), manifest.limitation());
    }

    private RiskSignalEvidence onlySignal(CustomerSnapshot snapshot) {
        return detector.detect(snapshot).getFirst();
    }

    private CustomerSnapshot snapshot(Activity... activities) {
        return new CustomerSnapshot(
                UUID.fromString("77777777-7777-7777-7777-777777777777"),
                List.of(activities),
                List.of());
    }

    private Activity card(int suffix, String status, String merchant) {
        return new Activity(
                id(suffix), Activity.ActivityType.CARD, new BigDecimal("100.00"), "CHF", status, time(suffix),
                new Activity.CardDetails("****0000", "VISA", merchant, "0000", true, "AUTH", null));
    }

    private Activity payment(int suffix, String status, String country, String account) {
        return new Activity(
                id(suffix), Activity.ActivityType.PAYMENT, new BigDecimal("1000.00"), "CHF", status, time(suffix),
                new Activity.PaymentDetails("BANK_TRANSFER", "CH00-SENDER", account, country));
    }

    private Activity crypto(int suffix, String status, String wallet) {
        return new Activity(
                id(suffix), Activity.ActivityType.CRYPTO, new BigDecimal("1.00"), "ETH", status, time(suffix),
                new Activity.CryptoDetails("Ethereum", wallet, "0xto", "hash", "Synthetic Exchange"));
    }

    private Instant time(int suffix) {
        return Instant.parse("2026-08-30T08:00:00Z").plusSeconds(suffix);
    }

    private UUID id(int suffix) {
        return UUID.fromString("eeeeeeee-eeee-eeee-eeee-" + String.format("%012d", suffix));
    }
}
