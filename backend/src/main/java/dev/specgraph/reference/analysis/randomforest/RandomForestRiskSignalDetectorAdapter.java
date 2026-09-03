package dev.specgraph.reference.analysis.randomforest;

import dev.specgraph.reference.analysis.RiskSignalDetectorPort;
import dev.specgraph.reference.analysis.RiskSignalEvidence;
import dev.specgraph.reference.customer.CustomerSnapshot;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.tribuo.Model;
import org.tribuo.Prediction;
import org.tribuo.VariableInfo;
import org.tribuo.classification.Label;
import org.tribuo.impl.ArrayExample;

/** Inference-only adapter for a previously trained, manifest-pinned Tribuo protobuf model. */
public final class RandomForestRiskSignalDetectorAdapter implements RiskSignalDetectorPort {
    public static final String DETECTOR_IDENTITY = "random-forest-review-v1";
    public static final String SIGNAL_IDENTITY = "random-forest-review-elevation-vote";
    public static final String BASELINE_LABEL = "BASELINE";
    public static final String ELEVATED_LABEL = "REVIEW_ELEVATED";
    private static final Set<String> EXPECTED_LABELS = Set.of(BASELINE_LABEL, ELEVATED_LABEL);

    private final Model<Label> model;
    private final RandomForestModelManifest manifest;

    public RandomForestRiskSignalDetectorAdapter(byte[] protobuf, RandomForestModelManifest manifest) {
        this.manifest = Objects.requireNonNull(manifest, "manifest");
        byte[] immutableBytes = Objects.requireNonNull(protobuf, "protobuf").clone();
        if (!sha256(immutableBytes).equals(manifest.artifactSha256())) {
            throw new IllegalArgumentException("random-forest model SHA-256 does not match manifest");
        }
        if (!RandomForestRiskFeatures.SCHEMA_VERSION.equals(manifest.featureSchemaVersion())) {
            throw new IllegalArgumentException("unsupported random-forest feature schema");
        }
        if (manifest.outputLabels().size() != EXPECTED_LABELS.size()
                || !new LinkedHashSet<>(manifest.outputLabels()).equals(EXPECTED_LABELS)) {
            throw new IllegalArgumentException("manifest output labels do not match detector contract");
        }
        try {
            Model<?> loaded = Model.deserializeFromStream(new ByteArrayInputStream(immutableBytes));
            this.model = loaded.castModel(Label.class);
        } catch (IOException | ClassCastException exception) {
            throw new IllegalArgumentException("invalid random-forest protobuf model", exception);
        }
        validateDomains();
    }

    @Override
    public List<RiskSignalEvidence> detect(CustomerSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (snapshot.activities().isEmpty()) {
            return List.of();
        }

        RandomForestRiskFeatures features = RandomForestRiskFeatures.from(snapshot);
        Prediction<Label> prediction = model.predict(new ArrayExample<>(
                new Label(Label.UNKNOWN), features.names(), features.values()));
        Label elevated = prediction.getOutputScores().get(ELEVATED_LABEL);
        double score = elevated == null ? Double.NaN : elevated.getScore();
        if (!Double.isFinite(score) || score < 0.0 || score > 1.0) {
            throw new IllegalStateException("random-forest elevated vote share is not in [0,1]");
        }

        LinkedHashMap<String, String> provenance = new LinkedHashMap<>();
        provenance.put("detectorFamily", "RANDOM_FOREST");
        provenance.put("semantics", manifest.scoreSemantics());
        provenance.put("predictedClass", prediction.getOutput().getLabel());
        provenance.put("modelVersion", manifest.modelVersion());
        provenance.put("modelSha256", manifest.artifactSha256());
        provenance.put("featureSchemaVersion", manifest.featureSchemaVersion());
        provenance.put("orderedFeatures", String.join(",", RandomForestRiskFeatures.ORDERED_NAMES));
        provenance.put("trainingDatasetIdentity", manifest.trainingDatasetIdentity());
        provenance.put("trainingPartitionSha256", manifest.trainingPartitionSha256());
        provenance.put("splitIdentity", manifest.splitIdentity());
        provenance.put("labelDefinitionIdentity", manifest.labelDefinitionIdentity());
        provenance.put("trainingSeed", Long.toString(manifest.trainingSeed()));
        provenance.put("treeSeed", Long.toString(manifest.treeSeed()));
        provenance.put("treeCount", Integer.toString(manifest.treeCount()));
        provenance.put("maxDepth", Integer.toString(manifest.maxDepth()));
        provenance.put("featureSubsampling", String.format(Locale.ROOT, "%.2f", manifest.featureSubsampling()));
        provenance.put("library", manifest.libraryVersion());
        provenance.put("inferenceMode", "fixed-protobuf-model; no request-time training");
        provenance.put("features", format(features.values()));
        provenance.put("demoLimitation", manifest.limitation());
        return List.of(new RiskSignalEvidence(DETECTOR_IDENTITY, SIGNAL_IDENTITY, score, Map.copyOf(provenance)));
    }

    private void validateDomains() {
        Set<String> modelFeatures = StreamSupport.stream(model.getFeatureIDMap().spliterator(), false)
                .map(VariableInfo::getName)
                .collect(Collectors.toSet());
        if (!modelFeatures.equals(Set.copyOf(RandomForestRiskFeatures.ORDERED_NAMES))) {
            throw new IllegalArgumentException("model feature domain does not match detector schema");
        }
        Set<String> modelLabels = model.getOutputIDInfo().getDomain().stream()
                .map(Label::getLabel)
                .collect(Collectors.toSet());
        if (!modelLabels.equals(EXPECTED_LABELS)) {
            throw new IllegalArgumentException("model output domain does not match detector contract");
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String format(double[] values) {
        return java.util.Arrays.stream(values)
                .mapToObj(value -> String.format(Locale.ROOT, "%.6f", value))
                .collect(Collectors.joining(","));
    }
}
