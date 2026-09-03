package dev.specgraph.reference.customer;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("VFY-CUSTOMER-READ-001")
final class SyntheticCustomerReviewQueryTests {
    private final SyntheticActivityAdapter adapter = new SyntheticActivityAdapter();

    @Test
    void appliesTypeStatusAndDateFiltersWithoutChangingSnapshotPortSemantics() {
        var cardPage = adapter.loadReviewPage(
                        SyntheticActivityAdapter.SEEDED_CUSTOMER_ID,
                        new CustomerReviewQuery(0, 50, Activity.ActivityType.CARD, null, null, null))
                .orElseThrow();
        assertThat(cardPage.activities()).singleElement().satisfies(activity ->
                assertThat(activity.type()).isEqualTo(Activity.ActivityType.CARD));
        assertThat(cardPage.riskEvidence()).hasSize(1);
        assertThat(cardPage.totalActivities()).isEqualTo(1);

        var pendingPage = adapter.loadReviewPage(
                        SyntheticActivityAdapter.SEEDED_CUSTOMER_ID,
                        new CustomerReviewQuery(0, 50, null, "pending", null, null))
                .orElseThrow();
        assertThat(pendingPage.activities()).singleElement().satisfies(activity ->
                assertThat(activity.type()).isEqualTo(Activity.ActivityType.CRYPTO));
        assertThat(pendingPage.totalRiskEvidence()).isEqualTo(1);

        var datePage = adapter.loadReviewPage(
                        SyntheticActivityAdapter.SEEDED_CUSTOMER_ID,
                        new CustomerReviewQuery(
                                0,
                                50,
                                null,
                                null,
                                Instant.parse("2026-08-29T00:00:00Z"),
                                Instant.parse("2026-08-30T00:00:00Z")))
                .orElseThrow();
        assertThat(datePage.activities()).singleElement().satisfies(activity ->
                assertThat(activity.type()).isEqualTo(Activity.ActivityType.PAYMENT));
        assertThat(datePage.riskEvidence()).isEmpty();

        assertThat(adapter.loadSnapshot(SyntheticActivityAdapter.SEEDED_CUSTOMER_ID).orElseThrow().activities())
                .hasSize(3);
    }

    @Test
    void pageBeyondAvailableDataIsExplicitlyEmptyButKeepsTotals() {
        var page = adapter.loadReviewPage(
                        SyntheticActivityAdapter.SEEDED_CUSTOMER_ID,
                        new CustomerReviewQuery(1, 2, null, null, null, null))
                .orElseThrow();

        assertThat(page.activities()).hasSize(1);
        assertThat(page.totalActivities()).isEqualTo(3);
        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(page.hasPrevious()).isTrue();
        assertThat(page.hasNext()).isFalse();

        var emptyPage = adapter.loadReviewPage(
                        SyntheticActivityAdapter.SEEDED_CUSTOMER_ID,
                        new CustomerReviewQuery(2, 2, null, null, null, null))
                .orElseThrow();
        assertThat(emptyPage.activities()).isEmpty();
        assertThat(emptyPage.totalActivities()).isEqualTo(3);
        assertThat(emptyPage.hasPrevious()).isTrue();
        assertThat(emptyPage.hasNext()).isFalse();
    }
}
