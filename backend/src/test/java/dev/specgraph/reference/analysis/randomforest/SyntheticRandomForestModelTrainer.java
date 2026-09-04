package dev.specgraph.reference.analysis.randomforest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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
import org.tribuo.datasource.ListDataSource;
import org.tribuo.impl.ArrayExample;
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
    static final float FEATURE_SUBSAMPLING = 0.70f;
    private static final OffsetDateTime DATASET_CREATED_AT = OffsetDateTime.parse("2026-09-04T00:00:00Z");

    private SyntheticRandomForestModelTrainer() {}

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
        var tree = new CARTClassificationTrainer(MAX_DEPTH, FEATURE_SUBSAMPLING, false, TREE_SEED);
        var forest = new RandomForestTrainer<Label>(
                tree, new VotingCombiner(), TREE_COUNT, TRAINING_SEED);
        Model<Label> model = forest.train(dataset);
        try (var output = new ByteArrayOutputStream()) {
            model.serializeToStream(output);
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
                    "hand-assigned synthetic labels separable by construction; no production AML accuracy claim"));
        } catch (IOException exception) {
            throw new IllegalStateException("could not serialize trained random-forest model", exception);
        }
    }

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

    record TrainingRow(String scenarioId, RandomForestRiskFeatures features, boolean reviewElevated) {
        TrainingRow {
            if (scenarioId == null || scenarioId.isBlank()) {
                throw new IllegalArgumentException("scenarioId must not be blank");
            }
        }
    }

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
