package dev.specgraph.reference.analysis.randomforest;

import dev.specgraph.reference.analysis.RiskSignalDetectorPort;
import dev.specgraph.reference.analysis.RiskSignalEvidence;
import dev.specgraph.reference.customer.CustomerSnapshot;
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
import com.oracle.labs.mlrg.olcut.provenance.ConfiguredObjectProvenance;
import com.oracle.labs.mlrg.olcut.provenance.PrimitiveProvenance;
import com.oracle.labs.mlrg.olcut.provenance.Provenance;
import org.tribuo.Model;
import org.tribuo.Prediction;
import org.tribuo.VariableInfo;
import org.tribuo.classification.Label;
import org.tribuo.common.tree.TreeModel;
import org.tribuo.ensemble.WeightedEnsembleModel;
import org.tribuo.impl.ArrayExample;
import org.tribuo.protos.core.ModelProto;
import org.tribuo.provenance.TrainerProvenance;

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
            ModelProto serialized = ModelProto.parseFrom(immutableBytes);
            if (!WeightedEnsembleModel.class.getName().equals(serialized.getClassName())) {
                throw new IllegalArgumentException("protobuf does not declare the expected ensemble model class");
            }
            Model<?> loaded = Model.deserialize(serialized);
            this.model = loaded.castModel(Label.class);
        } catch (IOException | RuntimeException exception) {
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
        provenance.put("featureSubsampling", Double.toString(manifest.featureSubsampling()));
        provenance.put("library", manifest.libraryVersion());
        provenance.put("inferenceMode", "fixed-protobuf-model; no request-time training");
        provenance.put("features", format(features.values()));
        provenance.put("demoLimitation", manifest.limitation());
        return List.of(new RiskSignalEvidence(DETECTOR_IDENTITY, SIGNAL_IDENTITY, score, Map.copyOf(provenance)));
    }

    private void validateDomains() {
        if (!(model instanceof WeightedEnsembleModel<?> ensemble)) {
            throw new IllegalArgumentException("model is not a weighted random-forest ensemble");
        }
        if (ensemble.getNumModels() != manifest.treeCount()) {
            throw new IllegalArgumentException("model tree count does not match manifest");
        }
        if (ensemble.getModels().stream().anyMatch(member -> !(member instanceof TreeModel<?>))) {
            throw new IllegalArgumentException("random-forest ensemble contains a non-tree model");
        }
        if (ensemble.getModels().stream()
                .map(member -> (TreeModel<?>) member)
                .anyMatch(tree -> tree.getDepth() > manifest.maxDepth())) {
            throw new IllegalArgumentException("model tree depth exceeds manifest maximum");
        }
        String serializedTribuoVersion = model.getProvenance().getTribuoVersion();
        if (!manifest.libraryVersion().equals("tribuo-" + serializedTribuoVersion)) {
            throw new IllegalArgumentException("model Tribuo version does not match manifest");
        }
        validateTrainerProvenance(ensemble);
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

    private void validateTrainerProvenance(WeightedEnsembleModel<?> ensemble) {
        TrainerProvenance forest = model.getProvenance().getTrainerProvenance();
        if (!"org.tribuo.common.tree.RandomForestTrainer".equals(forest.getClassName())) {
            throw new IllegalArgumentException("model trainer provenance is not RandomForestTrainer");
        }
        Map<String, Provenance> forestParameters = forest.getConfiguredParameters();
        requireIntegral(forestParameters, "seed", manifest.trainingSeed());
        requireIntegral(forestParameters, "numMembers", manifest.treeCount());
        Provenance inner = forestParameters.get("innerTrainer");
        if (!(inner instanceof TrainerProvenance treeTrainer)) {
            throw new IllegalArgumentException("model trainer provenance has no tree trainer");
        }
        validateTreeTrainer(treeTrainer);
        Provenance combiner = forestParameters.get("combiner");
        if (!(combiner instanceof ConfiguredObjectProvenance configuredCombiner)
                || !"org.tribuo.classification.ensemble.VotingCombiner".equals(configuredCombiner.getClassName())) {
            throw new IllegalArgumentException("model trainer provenance has an unexpected ensemble combiner");
        }
        for (Model<?> member : ensemble.getModels()) {
            validateTreeTrainer(member.getProvenance().getTrainerProvenance());
        }
    }

    private void validateTreeTrainer(TrainerProvenance trainer) {
        if (!"org.tribuo.classification.dtree.CARTClassificationTrainer".equals(trainer.getClassName())) {
            throw new IllegalArgumentException("model member provenance is not CARTClassificationTrainer");
        }
        Map<String, Provenance> parameters = trainer.getConfiguredParameters();
        requireIntegral(parameters, "maxDepth", manifest.maxDepth());
        requireIntegral(parameters, "seed", manifest.treeSeed());
        requireDecimal(parameters, "fractionFeaturesInSplit", manifest.featureSubsampling());
    }

    private static void requireIntegral(Map<String, Provenance> parameters, String key, long expected) {
        Provenance value = parameters.get(key);
        if (!(value instanceof PrimitiveProvenance<?> primitive)
                || !(primitive.getValue() instanceof Number number)
                || !matchesIntegralProvenance(number, expected)) {
            throw new IllegalArgumentException("model trainer provenance does not match manifest: " + key);
        }
    }

    private static void requireDecimal(Map<String, Provenance> parameters, String key, double expected) {
        Provenance value = parameters.get(key);
        if (!(value instanceof PrimitiveProvenance<?> primitive)
                || !(primitive.getValue() instanceof Number number)) {
            throw new IllegalArgumentException("model trainer provenance does not match manifest: " + key);
        }
        if (!matchesDecimalProvenance(number, expected)) {
            throw new IllegalArgumentException("model trainer provenance does not match manifest: " + key);
        }
    }

    static boolean matchesIntegralProvenance(Number actual, long expected) {
        return (actual instanceof Byte
                        || actual instanceof Short
                        || actual instanceof Integer
                        || actual instanceof Long)
                && actual.longValue() == expected;
    }

    static boolean matchesDecimalProvenance(Number actual, double expected) {
        double value = actual.doubleValue();
        return Double.isFinite(value) && Math.abs(value - expected) <= 0.000001;
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
