package dev.specgraph.reference.analysis;

import dev.specgraph.reference.customer.Activity;
import dev.specgraph.reference.customer.CustomerSnapshot;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.math3.distribution.BetaDistribution;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Transparent synthetic-demo Bayesian detector. It estimates the posterior probability that the
 * customer's rate of explicitly defined review-elevated observations exceeds a fixed reference
 * rate. The output is derived evidence only and never mutates or replaces source risk facts.
 */
@Component
@Profile("bayesian-detector")
final class BayesianSequentialRiskSignalDetectorAdapter implements RiskSignalDetectorPort {
    static final String DETECTOR_IDENTITY = "beta-binomial-review-elevation-v1";
    static final String SIGNAL_IDENTITY = "posterior-review-elevation-rate";
    static final double PRIOR_ALPHA = 1.0;
    static final double PRIOR_BETA = 4.0;
    static final double REFERENCE_RATE = 0.40;

    @Override
    public List<RiskSignalEvidence> detect(CustomerSnapshot snapshot) {
        int observations = snapshot.activities().size();
        if (observations == 0) {
            return List.of();
        }

        long elevated = snapshot.activities().stream()
                .filter(BayesianSequentialRiskSignalDetectorAdapter::isReviewElevated)
                .count();

        double posteriorAlpha = PRIOR_ALPHA + elevated;
        double posteriorBeta = PRIOR_BETA + observations - elevated;
        BetaDistribution posterior = new BetaDistribution(posteriorAlpha, posteriorBeta);
        double probabilityAboveReference = 1.0 - posterior.cumulativeProbability(REFERENCE_RATE);

        return List.of(new RiskSignalEvidence(
                DETECTOR_IDENTITY,
                SIGNAL_IDENTITY,
                probabilityAboveReference,
                Map.ofEntries(
                        Map.entry("semantics", "P(reviewElevatedRate>0.40)"),
                        Map.entry("featureFamily", "synthetic-review-elevated-observation-v1"),
                        Map.entry("prior", format(PRIOR_ALPHA) + "," + format(PRIOR_BETA)),
                        Map.entry("posterior", format(posteriorAlpha) + "," + format(posteriorBeta)),
                        Map.entry("referenceRate", format(REFERENCE_RATE)),
                        Map.entry("elevatedObservations", Long.toString(elevated)),
                        Map.entry("totalObservations", Integer.toString(observations)),
                        Map.entry("library", "apache-commons-math3-3.6.1"),
                        Map.entry("demoLimitation", "synthetic heuristic; not production AML calibration"))));
    }

    private static boolean isReviewElevated(Activity activity) {
        if (!"completed".equals(activity.status().trim().toLowerCase(Locale.ROOT))) {
            return true;
        }
        return switch (activity.type()) {
            case CRYPTO -> true;
            case PAYMENT -> {
                Activity.PaymentDetails payment = (Activity.PaymentDetails) activity.details();
                yield !"CH".equalsIgnoreCase(payment.receiverBankCountry());
            }
            case CARD -> false;
        };
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }
}
