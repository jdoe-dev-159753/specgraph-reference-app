package dev.specgraph.reference.analysis.randomforest;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
    private static final String FORMAT_VERSION = "1";
    private static final List<String> CANONICAL_KEYS = List.of(
            "format-version",
            "model-version",
            "artifact-sha256",
            "feature-schema-version",
            "training-dataset-identity",
            "training-partition-sha256",
            "split-identity",
            "training-seed",
            "tree-seed",
            "tree-count",
            "max-depth",
            "feature-subsampling",
            "library-version",
            "output-labels",
            "label-definition-identity",
            "score-semantics",
            "limitation");

    public RandomForestModelManifest {
        modelVersion = requireText(modelVersion, "modelVersion");
        artifactSha256 = requireSha256(artifactSha256, "artifactSha256");
        featureSchemaVersion = requireText(featureSchemaVersion, "featureSchemaVersion");
        trainingDatasetIdentity = requireText(trainingDatasetIdentity, "trainingDatasetIdentity");
        trainingPartitionSha256 = requireSha256(trainingPartitionSha256, "trainingPartitionSha256");
        splitIdentity = requireText(splitIdentity, "splitIdentity");
        libraryVersion = requireText(libraryVersion, "libraryVersion");
        outputLabels = List.copyOf(Objects.requireNonNull(outputLabels, "outputLabels"));
        if (outputLabels.isEmpty()
                || outputLabels.stream().anyMatch(label -> label == null || label.isBlank())
                || Set.copyOf(outputLabels).size() != outputLabels.size()) {
            throw new IllegalArgumentException("outputLabels must contain distinct non-blank labels");
        }
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

    static RandomForestModelManifest fromCanonicalProperties(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        String content = decodeUtf8(bytes);
        if (!content.endsWith("\n") || content.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("random-forest manifest must use canonical LF-terminated lines");
        }

        Map<String, String> values = new LinkedHashMap<>();
        for (String line : content.substring(0, content.length() - 1).split("\n", -1)) {
            int separator = line.indexOf('=');
            if (separator <= 0 || separator == line.length() - 1 || line.indexOf('=', separator + 1) >= 0) {
                throw new IllegalArgumentException("invalid random-forest manifest entry");
            }
            String key = line.substring(0, separator);
            String value = line.substring(separator + 1);
            if (!CANONICAL_KEYS.contains(key) || values.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("unknown or duplicate random-forest manifest key: " + key);
            }
        }
        if (!values.keySet().equals(Set.copyOf(CANONICAL_KEYS))) {
            throw new IllegalArgumentException("random-forest manifest keys are incomplete");
        }
        if (!FORMAT_VERSION.equals(values.get("format-version"))) {
            throw new IllegalArgumentException("unsupported random-forest manifest format");
        }

        try {
            return new RandomForestModelManifest(
                    values.get("model-version"),
                    values.get("artifact-sha256"),
                    values.get("feature-schema-version"),
                    values.get("training-dataset-identity"),
                    values.get("training-partition-sha256"),
                    values.get("split-identity"),
                    Long.parseLong(values.get("training-seed")),
                    Long.parseLong(values.get("tree-seed")),
                    Integer.parseInt(values.get("tree-count")),
                    Integer.parseInt(values.get("max-depth")),
                    Double.parseDouble(values.get("feature-subsampling")),
                    values.get("library-version"),
                    List.of(values.get("output-labels").split(",", -1)),
                    values.get("label-definition-identity"),
                    values.get("score-semantics"),
                    values.get("limitation"));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("invalid numeric value in random-forest manifest", exception);
        }
    }

    byte[] toCanonicalProperties() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("format-version", FORMAT_VERSION);
        values.put("model-version", modelVersion);
        values.put("artifact-sha256", artifactSha256);
        values.put("feature-schema-version", featureSchemaVersion);
        values.put("training-dataset-identity", trainingDatasetIdentity);
        values.put("training-partition-sha256", trainingPartitionSha256);
        values.put("split-identity", splitIdentity);
        values.put("training-seed", Long.toString(trainingSeed));
        values.put("tree-seed", Long.toString(treeSeed));
        values.put("tree-count", Integer.toString(treeCount));
        values.put("max-depth", Integer.toString(maxDepth));
        values.put("feature-subsampling", Double.toString(featureSubsampling));
        values.put("library-version", libraryVersion);
        values.put("output-labels", String.join(",", outputLabels));
        values.put("label-definition-identity", labelDefinitionIdentity);
        values.put("score-semantics", scoreSemantics);
        values.put("limitation", limitation);
        StringBuilder canonical = new StringBuilder();
        CANONICAL_KEYS.forEach(key -> canonical.append(key).append('=').append(values.get(key)).append('\n'));
        return canonical.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String decodeUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("random-forest manifest is not valid UTF-8", exception);
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
