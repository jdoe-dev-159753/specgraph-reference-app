package dev.specgraph.reference.analysis;

import dev.specgraph.reference.customer.Activity;
import dev.specgraph.reference.customer.CustomerSnapshot;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Small explainable fuzzy detector for the synthetic review domain.
 *
 * <p>The implementation deliberately keeps the fuzzy surface project-owned and bounded: four
 * normalized memberships, one coupled rule and monotonic weighted singleton defuzzification. This
 * avoids a heavyweight or license-constraining fuzzy runtime for a rule surface that is smaller
 * than the adapter glue such a dependency would require. The output is derived advisory evidence
 * only and never mutates source risk facts.
 */
@Component
final class FuzzyRiskSignalDetectorAdapter implements RiskSignalDetectorPort {
    static final String DETECTOR_IDENTITY = "graded-review-fuzzy-v1";
    static final String SIGNAL_IDENTITY = "fuzzy-review-elevation";
    static final String RULE_SET_VERSION = "review-fuzzy-rules-v2";
    static final String FEATURE_SCHEMA_VERSION = "review-fuzzy-features-v1";

    private static final double BASELINE_ACTIVATION = 0.25;
    private static final double BASELINE_CONSEQUENT = 0.05;
    private static final double ELEVATION_CONSEQUENT = 1.0;

    /**
     * Evaluates the complete snapshot against the versioned fuzzy surface and records every input
     * ratio and rule activation needed to explain the bounded score.
     */
    @Override
    public List<RiskSignalEvidence> detect(CustomerSnapshot snapshot) {
        int observations = snapshot.activities().size();
        if (observations == 0) {
            return List.of();
        }

        double crossBorderRatio = ratio(
                snapshot.activities().stream().filter(FuzzyRiskSignalDetectorAdapter::isCrossBorderPayment).count(),
                observations);
        double cryptoRatio = ratio(
                snapshot.activities().stream().filter(activity -> activity.type() == Activity.ActivityType.CRYPTO).count(),
                observations);
        double incompleteRatio = ratio(
                snapshot.activities().stream().filter(FuzzyRiskSignalDetectorAdapter::isIncomplete).count(),
                observations);
        double sourceRiskDensity = Math.min(1.0, ratio(snapshot.riskEvidence().size(), observations));

        LinkedHashMap<String, Double> activations = new LinkedHashMap<>();
        activations.put("R0_BASELINE", BASELINE_ACTIVATION);
        activations.put("R1_CRYPTO", rising(cryptoRatio, 0.00, 0.35));
        activations.put("R2_CROSS_BORDER", rising(crossBorderRatio, 0.10, 0.60));
        activations.put("R3_INCOMPLETE", rising(incompleteRatio, 0.10, 0.50));
        activations.put("R4_SOURCE_RISK", rising(sourceRiskDensity, 0.10, 0.60));
        activations.put("R5_CROSS_BORDER_WITH_SOURCE_RISK",
                Math.min(activations.get("R2_CROSS_BORDER"), activations.get("R4_SOURCE_RISK")));

        Map<String, Double> consequents = Map.of(
                "R0_BASELINE", BASELINE_CONSEQUENT,
                "R1_CRYPTO", ELEVATION_CONSEQUENT,
                "R2_CROSS_BORDER", ELEVATION_CONSEQUENT,
                "R3_INCOMPLETE", ELEVATION_CONSEQUENT,
                "R4_SOURCE_RISK", ELEVATION_CONSEQUENT,
                "R5_CROSS_BORDER_WITH_SOURCE_RISK", ELEVATION_CONSEQUENT);

        double weighted = 0.0;
        double activationSum = 0.0;
        for (Map.Entry<String, Double> activation : activations.entrySet()) {
            weighted += activation.getValue() * consequents.get(activation.getKey());
            activationSum += activation.getValue();
        }
        double score = clamp01(weighted / activationSum);

        LinkedHashMap<String, String> provenance = new LinkedHashMap<>();
        provenance.put("semantics", "monotonic weighted fuzzy review-elevation score in [0,1]");
        provenance.put("featureSchemaVersion", FEATURE_SCHEMA_VERSION);
        provenance.put("ruleSetVersion", RULE_SET_VERSION);
        provenance.put("defuzzification", "weighted-singleton-monotonic-v2");
        provenance.put("positiveConsequent", format(ELEVATION_CONSEQUENT));
        provenance.put("crossBorderRatio", format(crossBorderRatio));
        provenance.put("cryptoRatio", format(cryptoRatio));
        provenance.put("incompleteRatio", format(incompleteRatio));
        provenance.put("sourceRiskDensity", format(sourceRiskDensity));
        activations.forEach((rule, activation) -> provenance.put("activation." + rule, format(activation)));
        provenance.put("implementation", "project-owned-minimal-fuzzy-inference-v2");
        provenance.put("demoLimitation", "synthetic heuristic; not production AML calibration");

        return List.of(new RiskSignalEvidence(
                DETECTOR_IDENTITY,
                SIGNAL_IDENTITY,
                score,
                provenance));
    }

    private static boolean isCrossBorderPayment(Activity activity) {
        if (activity.type() != Activity.ActivityType.PAYMENT) {
            return false;
        }
        Activity.PaymentDetails payment = (Activity.PaymentDetails) activity.details();
        return !"CH".equalsIgnoreCase(payment.receiverBankCountry());
    }

    private static boolean isIncomplete(Activity activity) {
        return !"completed".equals(activity.status().trim().toLowerCase(Locale.ROOT));
    }

    private static double ratio(long numerator, int denominator) {
        return denominator == 0 ? 0.0 : (double) numerator / denominator;
    }

    /** Maps a feature onto a monotonic linear membership between its inactive and saturated bounds. */
    private static double rising(double value, double low, double high) {
        if (value <= low) {
            return 0.0;
        }
        if (value >= high) {
            return 1.0;
        }
        return (value - low) / (high - low);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }
}
