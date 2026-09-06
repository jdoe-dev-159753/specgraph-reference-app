package dev.specgraph.reference.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("VFY-HISTORY-001")
/** Verifies zero-based history bounds, stable offsets and rejection before persistence access. */
final class AnalysisHistoryQueryTests {

    @Test
    void defaultPageIsBoundedForProvenanceHeavyEntries() {
        var query = AnalysisHistoryQuery.firstPage();
        assertThat(query.page()).isZero();
        assertThat(query.pageSize()).isEqualTo(20);
        assertThat(query.offset()).isZero();
    }

    @Test
    void rejectsInvalidBoundsAndComputesStableOffset() {
        assertThat(new AnalysisHistoryQuery(3, 20).offset()).isEqualTo(60);
        assertThat(new AnalysisHistoryQuery(21_474_836, 100).offset()).isEqualTo(2_147_483_600L);
        assertThatThrownBy(() -> new AnalysisHistoryQuery(-1, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AnalysisHistoryQuery(0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AnalysisHistoryQuery(0, 101))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AnalysisHistoryQuery(21_474_837, 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pagination offset");
    }
}
