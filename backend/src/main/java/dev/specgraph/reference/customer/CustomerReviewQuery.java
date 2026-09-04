package dev.specgraph.reference.customer;

import java.time.Instant;

/**
 * Application-owned query contract for bounded operator-facing customer review.
 *
 * <p>The default and maximum page sizes are HTTP/UI workload bounds. They deliberately do not
 * encode model-context or token-budget semantics; #124 owns the separate provider-neutral analysis
 * context boundary.
 */
public record CustomerReviewQuery(
        int page,
        int pageSize,
        Activity.ActivityType activityType,
        String status,
        Instant createdFrom,
        Instant createdTo) {
    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int MAX_PAGE_SIZE = 200;

    public CustomerReviewQuery {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize must be between 1 and " + MAX_PAGE_SIZE);
        }
        if ((long) page * pageSize > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("page and pageSize exceed the supported pagination offset");
        }
        status = status == null || status.isBlank() ? null : status.trim();
        if (createdFrom != null && createdTo != null && !createdFrom.isBefore(createdTo)) {
            throw new IllegalArgumentException("createdFrom must be before createdTo");
        }
    }

    public static CustomerReviewQuery firstPage() {
        return new CustomerReviewQuery(0, DEFAULT_PAGE_SIZE, null, null, null, null);
    }

    public long offset() {
        return Math.multiplyExact((long) page, pageSize);
    }
}
