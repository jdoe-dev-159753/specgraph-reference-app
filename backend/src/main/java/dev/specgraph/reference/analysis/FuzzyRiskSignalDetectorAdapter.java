package dev.specgraph.reference.analysis;

import dev.specgraph.reference.customer.Activity;
import dev.specgraph.reference.customer.CustomerSnapshot;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Explainable fuzzy heuristic with overlap and small-sample shrinkage; not an AML probability.
 */
@Component
final class FuzzyRiskSignalDetectorAdapter implements RiskSignalDetectorPort {
    static final String DETECTOR_IDENTITY = "graded-review-fuzzy-v1";
    static final String SIGNAL_IDENTITY = "fuzzy-review-elevation";
    static final String RULE_SET_VERSION = "review-fuzzy-rules-v3";
    static final String FEATURE_SCHEMA_VERSION = "review-fuzzy-features-v2";

    private static final int SMALL_SAMPLE_PRIOR_OBSERVATIONS = 2;
    private static final double LOW_SHOULDER_END = 0.10;
    private static final double MEDIUM_PEAK = 0.30;
    private static final double HIGH_SHOULDER_START = 0.60;

    private static final double BASELINE_CONSEQUENT = 0.05;
    private static final double CRYPTO_CONSEQUENT = 0.10;
    private static final double INCOMPLETE_CONSEQUENT = 0.10;
    private static final double CROSS_BORDER_CONSEQUENT = 0.2625;
    private static final double SOURCE_RISK_CONSEQUENT = 0.4875;
    private static final double COUPLED_CONSEQUENT = 0.0;

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

        long crossBorderCount = snapshot.activities().stream()
                .filter(FuzzyRiskSignalDetectorAdapter::isCrossBorderPayment).count();
        long cryptoCount = snapshot.activities().stream()
                .filter(activity -> activity.type() == Activity.ActivityType.CRYPTO).count();
        long incompleteCount = snapshot.activities().stream()
                .filter(FuzzyRiskSignalDetectorAdapter::isIncomplete).count();
        long sourceRiskCount = Math.min(observations, snapshot.riskEvidence().size());

        FeatureInference crypto = infer(cryptoCount, observations);
        FeatureInference crossBorder = infer(crossBorderCount, observations);
        FeatureInference incomplete = infer(incompleteCount, observations);
        FeatureInference sourceRisk = infer(sourceRiskCount, observations);
        double coupledActivation = Math.min(crossBorder.degree(), sourceRisk.degree());

        double score = clamp01(
                BASELINE_CONSEQUENT
                        + CRYPTO_CONSEQUENT * crypto.degree()
                        + INCOMPLETE_CONSEQUENT * incomplete.degree()
                        + CROSS_BORDER_CONSEQUENT * crossBorder.degree()
                        + SOURCE_RISK_CONSEQUENT * sourceRisk.degree()
                        + COUPLED_CONSEQUENT * coupledActivation);

        LinkedHashMap<String, String> provenance = new LinkedHashMap<>();
        provenance.put("semantics", "heuristic monotone fuzzy review-elevation degree in [0,1]");
        provenance.put("calibration", "not an AML probability");
        provenance.put("featureSchemaVersion", FEATURE_SCHEMA_VERSION);
        provenance.put("ruleSetVersion", RULE_SET_VERSION);
        provenance.put("defuzzification", "fixed-weight-monotone-surface-v3");
        provenance.put("monotonicDimensions",
                "effective.cryptoRatio,effective.crossBorderRatio,effective.incompleteRatio,effective.sourceRiskRatio");
        provenance.put("smallSampleTreatment", "add-two zero-positive prior observations");
        provenance.put("observationCount", Integer.toString(observations));
        provenance.put("effectiveObservationCount",
                Integer.toString(observations + SMALL_SAMPLE_PRIOR_OBSERVATIONS));
        provenance.put("cryptoRatio", format(crypto.rawRatio()));
        provenance.put("crossBorderRatio", format(crossBorder.rawRatio()));
        provenance.put("incompleteRatio", format(incomplete.rawRatio()));
        provenance.put("sourceRiskDensity", format(sourceRisk.rawRatio()));

        addFeatureProvenance(provenance, "crypto", crypto);
        addFeatureProvenance(provenance, "crossBorder", crossBorder);
        addFeatureProvenance(provenance, "incomplete", incomplete);
        addFeatureProvenance(provenance, "sourceRisk", sourceRisk);

        provenance.put("activation.R0_BASELINE", "1.000000");
        provenance.put("activation.R1_CRYPTO", format(crypto.degree()));
        provenance.put("activation.R2_CROSS_BORDER", format(crossBorder.degree()));
        provenance.put("activation.R3_INCOMPLETE", format(incomplete.degree()));
        provenance.put("activation.R4_SOURCE_RISK", format(sourceRisk.degree()));
        provenance.put("activation.R5_CROSS_BORDER_WITH_SOURCE_RISK", format(coupledActivation));
        provenance.put("consequent.R0_BASELINE", format(BASELINE_CONSEQUENT));
        provenance.put("consequent.R1_CRYPTO", format(CRYPTO_CONSEQUENT));
        provenance.put("consequent.R2_CROSS_BORDER", format(CROSS_BORDER_CONSEQUENT));
        provenance.put("consequent.R3_INCOMPLETE", format(INCOMPLETE_CONSEQUENT));
        provenance.put("consequent.R4_SOURCE_RISK", format(SOURCE_RISK_CONSEQUENT));
        provenance.put("consequent.R5_CROSS_BORDER_WITH_SOURCE_RISK", format(COUPLED_CONSEQUENT));
        provenance.put("interactionPolicy",
                "diagnostic conjunction only; cross-border/source-risk consequents share a fixed 0.75 budget");
        provenance.put("implementation", "project-owned-overlapping-fuzzy-inference-v3");
        provenance.put("demoLimitation", "synthetic heuristic; not production AML calibration");

        return List.of(new RiskSignalEvidence(DETECTOR_IDENTITY, SIGNAL_IDENTITY, score, provenance));
    }

    private static FeatureInference infer(long positiveCount, int observations) {
        double rawRatio = ratio(positiveCount, observations);
        double effectiveRatio = ratio(positiveCount, observations + SMALL_SAMPLE_PRIOR_OBSERVATIONS);
        Membership membership = partition(effectiveRatio);
        double degree = 0.5 * membership.medium() + membership.high();
        return new FeatureInference(rawRatio, effectiveRatio, membership, degree);
    }

    private static Membership partition(double value) {
        if (value <= LOW_SHOULDER_END) {
            return new Membership(1.0, 0.0, 0.0);
        }
        if (value <= MEDIUM_PEAK) {
            double medium = rising(value, LOW_SHOULDER_END, MEDIUM_PEAK);
            return new Membership(1.0 - medium, medium, 0.0);
        }
        if (value < HIGH_SHOULDER_START) {
            double high = rising(value, MEDIUM_PEAK, HIGH_SHOULDER_START);
            return new Membership(0.0, 1.0 - high, high);
        }
        return new Membership(0.0, 0.0, 1.0);
    }

    private static void addFeatureProvenance(
            LinkedHashMap<String, String> provenance, String feature, FeatureInference inference) {
        provenance.put("raw." + feature + "Ratio", format(inference.rawRatio()));
        provenance.put("effective." + feature + "Ratio", format(inference.effectiveRatio()));
        provenance.put("membership." + feature + ".low", format(inference.membership().low()));
        provenance.put("membership." + feature + ".medium", format(inference.membership().medium()));
        provenance.put("membership." + feature + ".high", format(inference.membership().high()));
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
        return (value - low) / (high - low);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private record Membership(double low, double medium, double high) {}

    private record FeatureInference(
            double rawRatio, double effectiveRatio, Membership membership, double degree) {}
}
