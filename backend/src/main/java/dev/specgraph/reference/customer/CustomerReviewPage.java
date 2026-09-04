package dev.specgraph.reference.customer;

import dev.specgraph.reference.risk.RiskEvidence;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Bounded operator-facing view of one customer review page. */
public record CustomerReviewPage(
        UUID customerId,
        List<Activity> activities,
        List<RiskEvidence> riskEvidence,
        int page,
        int pageSize,
        long totalActivities,
        long totalRiskEvidence) {
    public CustomerReviewPage {
        Objects.requireNonNull(customerId, "customerId");
        activities = List.copyOf(Objects.requireNonNull(activities, "activities"));
        riskEvidence = List.copyOf(Objects.requireNonNull(riskEvidence, "riskEvidence"));
        if (page < 0 || pageSize < 1 || totalActivities < 0 || totalRiskEvidence < 0) {
            throw new IllegalArgumentException("page metadata must be non-negative and pageSize must be positive");
        }
        if (activities.size() > pageSize) {
            throw new IllegalArgumentException("activities exceed pageSize");
        }
    }

    public long totalPages() {
        return totalActivities == 0 ? 0 : (totalActivities + pageSize - 1) / pageSize;
    }

    public boolean hasPrevious() {
        return page > 0;
    }

    public boolean hasNext() {
        return ((long) page + 1) * pageSize < totalActivities;
    }
}
