package dev.specgraph.reference.analysis.randomforest;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;

/** Project-owned descriptive evidence about a window of Random Forest inputs. */
public record RandomForestFeatureDriftReport(
        String diagnosticVersion,
        String modelVersion,
        String featureSchemaVersion,
        String referenceDatasetIdentity,
        String referenceWindowIdentity,
        String referenceSha256,
        int referenceObservationCount,
        int observedObservationCount,
        int minimumObservationCount,
        double reviewThreshold,
        Map<String, Double> featureStatistics,
        OptionalDouble maximumStatistic,
        Status status,
        String metric,
        String metricLibrary,
        String semantics,
        String limitation) {

    private static final Set<String> EXPECTED_FEATURES = Set.copyOf(RandomForestRiskFeatures.ORDERED_NAMES);

    /**
     * Rejects reports whose counts, feature set, maximum statistic, threshold, and status disagree.
     */
    public RandomForestFeatureDriftReport {
        Objects.requireNonNull(maximumStatistic, "maximumStatistic");
        Objects.requireNonNull(status, "status");
        requireText(diagnosticVersion, modelVersion, featureSchemaVersion, referenceDatasetIdentity,
                referenceWindowIdentity, metric, metricLibrary, semantics, limitation);
        if (referenceSha256 == null || !referenceSha256.matches("[0-9a-f]{64}")
                || referenceObservationCount <= 0 || observedObservationCount < 0 || minimumObservationCount <= 0
                || !Double.isFinite(reviewThreshold) || reviewThreshold < 0.0 || reviewThreshold > 1.0) {
            throw new IllegalArgumentException("invalid drift diagnostic provenance, counts, or threshold");
        }
        featureStatistics = Collections.unmodifiableMap(new LinkedHashMap<>(
                Objects.requireNonNull(featureStatistics, "featureStatistics")));
        if (status == Status.INSUFFICIENT_OBSERVATIONS) {
            if (observedObservationCount >= minimumObservationCount
                    || !featureStatistics.isEmpty() || maximumStatistic.isPresent()) {
                throw new IllegalArgumentException("inconsistent insufficient-observations report");
            }
        } else {
            if (observedObservationCount < minimumObservationCount) {
                throw new IllegalArgumentException("calculated drift report has insufficient observations");
            }
            double computedMaximum = featureStatistics.values().stream()
                    .mapToDouble(RandomForestFeatureDriftReport::statistic).max().orElseThrow();
            if (!featureStatistics.keySet().equals(EXPECTED_FEATURES)
                    || maximumStatistic.isEmpty()
                    || Double.compare(computedMaximum, maximumStatistic.getAsDouble()) != 0
                    || (computedMaximum >= reviewThreshold) != (status == Status.REVIEW_TRIGGERED)) {
                throw new IllegalArgumentException("inconsistent calculated drift report");
            }
        }
    }

    public boolean reviewTriggered() {
        return status == Status.REVIEW_TRIGGERED;
    }

    /** Diagnostic outcome; a triggered review is evidence for inspection, not automatic retraining. */
    public enum Status {
        INSUFFICIENT_OBSERVATIONS,
        WITHIN_REVIEW_THRESHOLD,
        REVIEW_TRIGGERED
    }

    private static void requireText(String... values) {
        if (java.util.Arrays.stream(values).anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("drift diagnostic provenance and semantics must not be blank");
        }
    }

    private static double statistic(double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException("KS statistics must be finite and in [0,1]");
        }
        return value;
    }
}
