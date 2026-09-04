package dev.specgraph.reference.analysis.randomforest;

import dev.specgraph.reference.customer.CustomerSnapshot;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Properties;
import java.util.Set;
import org.apache.commons.math3.stat.inference.KolmogorovSmirnovTest;

/** Descriptive, inference-independent drift diagnostic for the packaged Random Forest input schema. */
public final class RandomForestFeatureDriftDiagnostic {
    static final String REFERENCE_RESOURCE =
            "dev/specgraph/reference/analysis/randomforest/"
                    + "synthetic-review-random-forest-v1-drift-reference.properties";
    static final String EXPECTED_REFERENCE_SHA256 =
            "b240a5947913b536ed963a3310a32673e7241f4077084842b65c0edb61bcc430";
    static final int MAX_OBSERVATIONS = 1_000;
    static final int MAX_ACTIVITIES = 10_000;
    private static final int MAX_REFERENCE_BYTES = 16 * 1024;
    private static final Set<String> EXPECTED_KEYS = Set.of(
            "format-version", "diagnostic-version", "model-version", "feature-schema-version",
            "reference-dataset-identity", "reference-window-identity", "metric", "metric-library",
            "review-threshold", "minimum-observations", "reference-observations",
            "activity-volume", "crypto-ratio", "cross-border-payment-ratio", "incomplete-ratio",
            "semantics", "limitation");

    private final Reference reference;

    public RandomForestFeatureDriftDiagnostic() {
        this(RandomForestFeatureDriftDiagnostic.class.getClassLoader());
    }

    RandomForestFeatureDriftDiagnostic(ClassLoader classLoader) {
        byte[] bytes = readRequiredResource(Objects.requireNonNull(classLoader, "classLoader"));
        if (!RandomForestRiskSignalDetectorRuntime.sha256(bytes).equals(EXPECTED_REFERENCE_SHA256)) {
            throw new IllegalStateException("packaged random-forest drift reference does not match its trust anchor");
        }
        try {
            reference = parse(bytes);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("packaged random-forest drift reference failed validation", exception);
        }
    }

    /** Returns observability evidence only; the status is never an automatic risk decision. */
    public RandomForestFeatureDriftReport assess(List<CustomerSnapshot> observationWindow) {
        Objects.requireNonNull(observationWindow, "observationWindow");
        if (observationWindow.size() > MAX_OBSERVATIONS) {
            throw new IllegalArgumentException("random-forest drift observation window exceeds maximum size");
        }
        List<RandomForestRiskFeatures> projected = new ArrayList<>(observationWindow.size());
        int activityCount = 0;
        for (CustomerSnapshot snapshot : observationWindow) {
            snapshot = Objects.requireNonNull(snapshot, "observationWindow entry");
            activityCount = Math.addExact(activityCount, snapshot.activities().size());
            if (activityCount > MAX_ACTIVITIES) {
                throw new IllegalArgumentException("random-forest drift activity window exceeds maximum size");
            }
            projected.add(RandomForestRiskFeatures.from(snapshot));
        }
        return assessFeatures(projected);
    }

    RandomForestFeatureDriftReport assessFeatures(List<RandomForestRiskFeatures> observationWindow) {
        List<RandomForestRiskFeatures> observations = List.copyOf(
                Objects.requireNonNull(observationWindow, "observationWindow"));
        if (observations.size() < reference.minimumObservations()) {
            return report(observations.size(), Map.of(), OptionalDouble.empty(),
                    RandomForestFeatureDriftReport.Status.INSUFFICIENT_OBSERVATIONS);
        }

        double[][] observed = transpose(observations);
        var ks = new KolmogorovSmirnovTest();
        LinkedHashMap<String, Double> statistics = new LinkedHashMap<>();
        double maximum = 0.0;
        for (int feature = 0; feature < RandomForestRiskFeatures.ORDERED_NAMES.size(); feature++) {
            double statistic = ks.kolmogorovSmirnovStatistic(reference.featureColumns()[feature], observed[feature]);
            statistics.put(RandomForestRiskFeatures.ORDERED_NAMES.get(feature), statistic);
            maximum = Math.max(maximum, statistic);
        }
        var status = maximum >= reference.reviewThreshold()
                ? RandomForestFeatureDriftReport.Status.REVIEW_TRIGGERED
                : RandomForestFeatureDriftReport.Status.WITHIN_REVIEW_THRESHOLD;
        return report(observations.size(), statistics, OptionalDouble.of(maximum), status);
    }

    private RandomForestFeatureDriftReport report(
            int observedCount, Map<String, Double> statistics, OptionalDouble maximum,
            RandomForestFeatureDriftReport.Status status) {
        return new RandomForestFeatureDriftReport(
                reference.diagnosticVersion(), reference.modelVersion(), reference.featureSchemaVersion(),
                reference.referenceDatasetIdentity(), reference.referenceWindowIdentity(), EXPECTED_REFERENCE_SHA256,
                reference.referenceObservations(), observedCount, reference.minimumObservations(),
                reference.reviewThreshold(), statistics, maximum, status, reference.metric(), reference.metricLibrary(),
                reference.semantics(), reference.limitation());
    }

    private static Reference parse(byte[] bytes) {
        String content = new String(bytes, StandardCharsets.UTF_8);
        if (!content.endsWith("\n") || content.indexOf('\r') >= 0
                || !java.util.Arrays.equals(bytes, content.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("random-forest drift reference must be canonical LF-terminated UTF-8");
        }
        Properties values = new Properties();
        try {
            values.load(new StringReader(content));
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
        if (!values.stringPropertyNames().equals(EXPECTED_KEYS) || !"1".equals(values.getProperty("format-version"))) {
            throw new IllegalArgumentException("unsupported or incomplete random-forest drift reference");
        }
        try {
            int referenceCount = Integer.parseInt(values.getProperty("reference-observations"));
            int minimumCount = Integer.parseInt(values.getProperty("minimum-observations"));
            double threshold = Double.parseDouble(values.getProperty("review-threshold"));
            if (referenceCount != 12 || minimumCount != 12 || threshold != 0.50
                    || !"synthetic-review-random-forest-feature-drift-v1"
                            .equals(values.getProperty("diagnostic-version"))
                    || !"synthetic-review-random-forest-v1".equals(values.getProperty("model-version"))
                    || !RandomForestRiskFeatures.SCHEMA_VERSION.equals(values.getProperty("feature-schema-version"))
                    || !"hand-authored-synthetic-feature-grid-v1"
                            .equals(values.getProperty("reference-dataset-identity"))
                    || !"hand-authored-training-partition-v1"
                            .equals(values.getProperty("reference-window-identity"))
                    || !"two-sample-kolmogorov-smirnov-d".equals(values.getProperty("metric"))
                    || !"apache-commons-math3-3.6.1".equals(values.getProperty("metric-library"))) {
                throw new IllegalArgumentException("unexpected random-forest drift reference provenance or policy");
            }
            double[][] columns = new double[RandomForestRiskFeatures.ORDERED_NAMES.size()][];
            for (int feature = 0; feature < columns.length; feature++) {
                columns[feature] = parseColumn(
                        values.getProperty(RandomForestRiskFeatures.ORDERED_NAMES.get(feature)), referenceCount);
            }
            return new Reference(
                    text(values, "diagnostic-version"), text(values, "model-version"),
                    values.getProperty("feature-schema-version"), text(values, "reference-dataset-identity"),
                    text(values, "reference-window-identity"), text(values, "metric"),
                    text(values, "metric-library"),
                    threshold, minimumCount, referenceCount, columns,
                    text(values, "semantics"), text(values, "limitation"));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("invalid numeric random-forest drift reference value", exception);
        }
    }

    private static double[] parseColumn(String value, int size) {
        String[] encoded = Objects.requireNonNull(value, "feature column").split(",", -1);
        if (encoded.length != size) {
            throw new IllegalArgumentException("random-forest drift feature column has an invalid size");
        }
        double[] column = new double[size];
        for (int index = 0; index < size; index++) {
            column[index] = Double.parseDouble(encoded[index]);
            if (!Double.isFinite(column[index]) || column[index] < 0.0 || column[index] > 1.0) {
                throw new IllegalArgumentException(
                        "random-forest drift reference features must be finite and in [0,1]");
            }
        }
        return column;
    }

    private static double[][] transpose(List<RandomForestRiskFeatures> rows) {
        double[][] columns = new double[RandomForestRiskFeatures.ORDERED_NAMES.size()][rows.size()];
        for (int row = 0; row < rows.size(); row++) {
            double[] values = rows.get(row).values();
            for (int feature = 0; feature < values.length; feature++) {
                columns[feature][row] = values[feature];
            }
        }
        return columns;
    }

    private static byte[] readRequiredResource(ClassLoader classLoader) {
        try (InputStream input = classLoader.getResourceAsStream(REFERENCE_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("required packaged random-forest drift reference is missing");
            }
            byte[] bytes = input.readNBytes(MAX_REFERENCE_BYTES + 1);
            if (bytes.length == 0 || bytes.length > MAX_REFERENCE_BYTES) {
                throw new IllegalStateException("packaged random-forest drift reference has an invalid size");
            }
            return bytes;
        } catch (IOException exception) {
            throw new IllegalStateException("could not read packaged random-forest drift reference", exception);
        }
    }

    private static String text(Properties values, String key) {
        String value = values.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " must not be blank");
        }
        return value;
    }

    private record Reference(
            String diagnosticVersion, String modelVersion, String featureSchemaVersion,
            String referenceDatasetIdentity, String referenceWindowIdentity, String metric, String metricLibrary,
            double reviewThreshold, int minimumObservations, int referenceObservations, double[][] featureColumns,
            String semantics, String limitation) {}
}
