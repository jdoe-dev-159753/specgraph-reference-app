package dev.specgraph.reference.analysis.randomforest;

import dev.specgraph.reference.customer.Activity;
import dev.specgraph.reference.customer.CustomerSnapshot;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Bounded, ordered, project-owned feature contract for the synthetic tree detector. */
record RandomForestRiskFeatures(
        double activityVolume,
        double cryptoRatio,
        double crossBorderPaymentRatio,
        double incompleteRatio) {
    static final String SCHEMA_VERSION = "review-random-forest-features-v1";
    static final List<String> ORDERED_NAMES = List.of(
            "activity-volume", "crypto-ratio", "cross-border-payment-ratio", "incomplete-ratio");

    RandomForestRiskFeatures {
        requireUnitInterval(activityVolume, "activityVolume");
        requireUnitInterval(cryptoRatio, "cryptoRatio");
        requireUnitInterval(crossBorderPaymentRatio, "crossBorderPaymentRatio");
        requireUnitInterval(incompleteRatio, "incompleteRatio");
    }

    static RandomForestRiskFeatures from(CustomerSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        int count = snapshot.activities().size();
        if (count == 0) {
            return new RandomForestRiskFeatures(0, 0, 0, 0);
        }
        long crypto = snapshot.activities().stream()
                .filter(activity -> activity.type() == Activity.ActivityType.CRYPTO)
                .count();
        long crossBorder = snapshot.activities().stream()
                .filter(RandomForestRiskFeatures::isCrossBorderPayment)
                .count();
        long incomplete = snapshot.activities().stream()
                .filter(activity -> !"completed".equals(activity.status().trim().toLowerCase(Locale.ROOT)))
                .count();
        return new RandomForestRiskFeatures(
                Math.min(1.0, count / 100.0),
                ratio(crypto, count),
                ratio(crossBorder, count),
                ratio(incomplete, count));
    }

    String[] names() {
        return ORDERED_NAMES.toArray(String[]::new);
    }

    double[] values() {
        return new double[] {activityVolume, cryptoRatio, crossBorderPaymentRatio, incompleteRatio};
    }

    private static boolean isCrossBorderPayment(Activity activity) {
        if (activity.type() != Activity.ActivityType.PAYMENT) {
            return false;
        }
        Activity.PaymentDetails details = (Activity.PaymentDetails) activity.details();
        return !"CH".equalsIgnoreCase(details.receiverBankCountry());
    }

    private static double ratio(long numerator, int denominator) {
        return (double) numerator / denominator;
    }

    private static void requireUnitInterval(double value, String field) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(field + " must be finite and in [0,1]");
        }
    }
}
