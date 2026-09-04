package dev.specgraph.reference.analysis.randomforest;

import java.util.List;
import java.util.Objects;

/** Project-owned trust anchor and provenance for one immutable packaged model. */
public record RandomForestModelManifest(
        String modelVersion,
        String artifactSha256,
        String featureSchemaVersion,
        String trainingDatasetIdentity,
        String trainingPartitionSha256,
        String splitIdentity,
        long trainingSeed,
        long treeSeed,
        int treeCount,
        int maxDepth,
        double featureSubsampling,
        String libraryVersion,
        List<String> outputLabels,
        String labelDefinitionIdentity,
        String scoreSemantics,
        String limitation) {
    public RandomForestModelManifest {
        modelVersion = requireText(modelVersion, "modelVersion");
        artifactSha256 = requireSha256(artifactSha256, "artifactSha256");
        featureSchemaVersion = requireText(featureSchemaVersion, "featureSchemaVersion");
        trainingDatasetIdentity = requireText(trainingDatasetIdentity, "trainingDatasetIdentity");
        trainingPartitionSha256 = requireSha256(trainingPartitionSha256, "trainingPartitionSha256");
        splitIdentity = requireText(splitIdentity, "splitIdentity");
        libraryVersion = requireText(libraryVersion, "libraryVersion");
        outputLabels = List.copyOf(Objects.requireNonNull(outputLabels, "outputLabels"));
        labelDefinitionIdentity = requireText(labelDefinitionIdentity, "labelDefinitionIdentity");
        scoreSemantics = requireText(scoreSemantics, "scoreSemantics");
        limitation = requireText(limitation, "limitation");
        if (treeCount <= 0
                || maxDepth <= 0
                || !Double.isFinite(featureSubsampling)
                || featureSubsampling <= 0.0
                || featureSubsampling > 1.0) {
            throw new IllegalArgumentException("invalid random-forest hyperparameters");
        }
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static String requireSha256(String value, String field) {
        value = requireText(value, field);
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " must be a lowercase SHA-256 digest");
        }
        return value;
    }
}
