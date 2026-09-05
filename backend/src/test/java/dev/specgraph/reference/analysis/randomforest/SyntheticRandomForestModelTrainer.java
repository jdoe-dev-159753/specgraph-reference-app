package dev.specgraph.reference.analysis.randomforest;

import com.google.protobuf.Any;
import com.oracle.labs.mlrg.olcut.config.protobuf.protos.ObjectProvenanceProto;
import com.oracle.labs.mlrg.olcut.config.protobuf.protos.RootProvenanceProto;
import com.oracle.labs.mlrg.olcut.config.protobuf.protos.SimpleProvenanceProto;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import org.tribuo.Example;
import org.tribuo.Model;
import org.tribuo.MutableDataset;
import org.tribuo.classification.Label;
import org.tribuo.classification.LabelFactory;
import org.tribuo.classification.dtree.CARTClassificationTrainer;
import org.tribuo.classification.ensemble.VotingCombiner;
import org.tribuo.common.tree.RandomForestTrainer;
import org.tribuo.common.tree.protos.TreeModelProto;
import org.tribuo.datasource.ListDataSource;
import org.tribuo.impl.ArrayExample;
import org.tribuo.protos.core.ModelDataProto;
import org.tribuo.protos.core.ModelProto;
import org.tribuo.protos.core.WeightedEnsembleModelProto;
import org.tribuo.provenance.SimpleDataSourceProvenance;

/** Test/tool boundary for reproducible offline model generation; never shipped in the runtime jar. */
final class SyntheticRandomForestModelTrainer {
    static final String MODEL_VERSION = "synthetic-review-random-forest-v1";
    static final String DATASET_IDENTITY = "hand-authored-synthetic-feature-grid-v1";
    static final String SPLIT_IDENTITY = "hand-authored-training-partition-v1";
    static final String LABEL_DEFINITION_IDENTITY = "hand-assigned-synthetic-review-labels-v1";
    static final long TRAINING_SEED = 20260904L;
    static final long TREE_SEED = 20260905L;
    static final int TREE_COUNT = 31;
    static final int MAX_DEPTH = 6;
    static final double FEATURE_SUBSAMPLING = 0.70;
    private static final OffsetDateTime DATASET_CREATED_AT = OffsetDateTime.parse("2026-09-04T00:00:00Z");
    private static final String LIMITATION = "hand-assigned synthetic labels separable by construction; "
            + "embedded Tribuo trained-at is a deterministic serialization sentinel, not an actual training timestamp; "
            + "no production AML accuracy claim";

    private SyntheticRandomForestModelTrainer() {}

    /** Trains the reproducible fixture and returns bytes paired with their complete trust manifest. */
    static GeneratedModel train(List<TrainingRow> rows) {
        if (rows.size() < 4
                || rows.stream().noneMatch(TrainingRow::reviewElevated)
                || rows.stream().allMatch(TrainingRow::reviewElevated)) {
            throw new IllegalArgumentException("training partition must contain at least four rows and both labels");
        }
        LabelFactory factory = new LabelFactory();
        List<Example<Label>> examples = new ArrayList<>(rows.size());
        for (TrainingRow row : rows) {
            examples.add(new ArrayExample<>(
                    new Label(row.reviewElevated()
                            ? RandomForestRiskSignalDetectorAdapter.ELEVATED_LABEL
                            : RandomForestRiskSignalDetectorAdapter.BASELINE_LABEL),
                    row.features().names(),
                    row.features().values()));
        }
        var source = new ListDataSource<>(
                examples,
                factory,
                new SimpleDataSourceProvenance(DATASET_IDENTITY, DATASET_CREATED_AT, factory));
        var dataset = new MutableDataset<>(source);
        var tree = new CARTClassificationTrainer(MAX_DEPTH, (float) FEATURE_SUBSAMPLING, false, TREE_SEED);
        var forest = new RandomForestTrainer<Label>(
                tree, new VotingCombiner(), TREE_COUNT, TRAINING_SEED);
        Model<Label> model = forest.train(dataset);
        try (var output = new ByteArrayOutputStream()) {
            canonicalize(model.serialize()).writeTo(output);
            byte[] protobuf = output.toByteArray();
            return new GeneratedModel(protobuf, new RandomForestModelManifest(
                    MODEL_VERSION,
                    sha256(protobuf),
                    RandomForestRiskFeatures.SCHEMA_VERSION,
                    DATASET_IDENTITY,
                    trainingPartitionHash(rows),
                    SPLIT_IDENTITY,
                    TRAINING_SEED,
                    TREE_SEED,
                    TREE_COUNT,
                    MAX_DEPTH,
                    FEATURE_SUBSAMPLING,
                    "tribuo-4.3.2",
                    List.of(
                            RandomForestRiskSignalDetectorAdapter.BASELINE_LABEL,
                            RandomForestRiskSignalDetectorAdapter.ELEVATED_LABEL),
                    LABEL_DEFINITION_IDENTITY,
                    "unweighted forest vote share for REVIEW_ELEVATED in [0,1]",
                    LIMITATION));
        } catch (IOException exception) {
            throw new IllegalStateException("could not serialize trained random-forest model", exception);
        }
    }

    /** Writes the generated model and manifest together so runtime fixtures cannot drift independently. */
    static void writePackagedResources(Path resourceRoot) {
        GeneratedModel generated = train(trainingPartition());
        Path packageDirectory = resourceRoot.resolve("dev/specgraph/reference/analysis/randomforest");
        try {
            Files.createDirectories(packageDirectory);
            Files.write(packageDirectory.resolve("synthetic-review-random-forest-v1.pb"), generated.protobuf());
            Files.write(
                    packageDirectory.resolve("synthetic-review-random-forest-v1.properties"),
                    generated.manifest().toCanonicalProperties());
        } catch (IOException exception) {
            throw new IllegalStateException("could not write packaged random-forest resources", exception);
        }
    }

    /** Removes serialization variability recursively from ensemble and member metadata. */
    private static ModelProto canonicalize(ModelProto model) throws IOException {
        WeightedEnsembleModelProto ensemble = model.getSerializedData().unpack(WeightedEnsembleModelProto.class);
        var ensembleBuilder = ensemble.toBuilder()
                .setMetadata(canonicalize(ensemble.getMetadata(), "synthetic-review-random-forest-v1"))
                .clearModels();
        for (int index = 0; index < ensemble.getModelsCount(); index++) {
            ModelProto member = ensemble.getModels(index);
            TreeModelProto tree = member.getSerializedData().unpack(TreeModelProto.class);
            TreeModelProto canonicalTree = tree.toBuilder()
                    .setMetadata(canonicalize(tree.getMetadata(), "synthetic-review-tree-" + index))
                    .build();
            ensembleBuilder.addModels(member.toBuilder()
                    .setSerializedData(Any.pack(canonicalTree))
                    .build());
        }
        return model.toBuilder()
                .setSerializedData(Any.pack(ensembleBuilder.build()))
                .build();
    }

    /** Replaces generated model names and provenance with stable fixture identities. */
    private static ModelDataProto canonicalize(ModelDataProto metadata, String name) {
        return metadata.toBuilder()
                .setName(name)
                .setProvenance(canonicalize(metadata.getProvenance()))
                .build();
    }

    /** Rewrites environment-dependent provenance while preserving trainer parameters under test. */
    private static RootProvenanceProto canonicalize(RootProvenanceProto provenance) {
        var builder = provenance.toBuilder().clearOmp().clearSmp();
        var canonicalNames = new HashMap<String, String>();
        for (ObjectProvenanceProto object : provenance.getOmpList()) {
            String canonicalName = object.getObjectClassName() + "#" + object.getIndex();
            canonicalNames.put(object.getObjectName(), canonicalName);
            builder.addOmp(object.toBuilder()
                    .setObjectName(canonicalName)
                    .build());
        }
        for (SimpleProvenanceProto simple : provenance.getSmpList()) {
            String value = simple.getIsReference()
                    ? canonicalNames.getOrDefault(simple.getValue(), simple.getValue())
                    : switch (simple.getKey()) {
                case "trained-at" -> DATASET_CREATED_AT.toString();
                case "java-version" -> "canonical-jdk-21";
                case "os-name" -> "canonical-os";
                case "os-arch" -> "canonical-arch";
                default -> simple.getValue();
            };
            builder.addSmp(simple.toBuilder().setValue(value).build());
        }
        return builder.build();
    }

    /** Returns the fixed, balanced feature grid whose labels test mechanics rather than AML accuracy. */
    static List<TrainingRow> trainingPartition() {
        // Labels are hand-assigned for adapter mechanics, never derived from source risk_assessments.
        return List.of(
                row("baseline-01", .03, .00, .00, .00, false),
                row("baseline-02", .05, .00, .10, .00, false),
                row("baseline-03", .08, .10, .00, .10, false),
                row("baseline-04", .12, .00, .20, .10, false),
                row("baseline-05", .18, .10, .10, .20, false),
                row("baseline-06", .25, .20, .20, .10, false),
                row("review-01", .05, .60, .40, .40, true),
                row("review-02", .08, .40, .70, .30, true),
                row("review-03", .12, .50, .50, .60, true),
                row("review-04", .18, .70, .30, .50, true),
                row("review-05", .25, .30, .80, .70, true),
                row("review-06", .40, .60, .60, .40, true));
    }

    private static TrainingRow row(
            String scenarioId, double volume, double crypto, double crossBorder, double incomplete, boolean elevated) {
        return new TrainingRow(
                scenarioId, new RandomForestRiskFeatures(volume, crypto, crossBorder, incomplete), elevated);
    }

    /** Hashes canonical identities, labels, and exact IEEE-754 feature values in row order. */
    private static String trainingPartitionHash(List<TrainingRow> rows) {
        StringBuilder canonical = new StringBuilder(DATASET_IDENTITY).append('|').append(SPLIT_IDENTITY);
        for (TrainingRow row : rows) {
            canonical.append('\n').append(row.scenarioId()).append('|').append(row.reviewElevated());
            for (double value : row.features().values()) {
                canonical.append('|').append(Double.toHexString(value));
            }
        }
        return sha256(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    /** One synthetic feature row and its handcrafted scenario label. */
    record TrainingRow(String scenarioId, RandomForestRiskFeatures features, boolean reviewElevated) {
        TrainingRow {
            if (scenarioId == null || scenarioId.isBlank()) {
                throw new IllegalArgumentException("scenarioId must not be blank");
            }
        }
    }

    /** Complete deterministic training output used to reproduce packaged runtime artifacts. */
    record GeneratedModel(byte[] protobuf, RandomForestModelManifest manifest) {
        GeneratedModel {
            protobuf = protobuf.clone();
        }

        @Override
        public byte[] protobuf() {
            return protobuf.clone();
        }
    }
}
