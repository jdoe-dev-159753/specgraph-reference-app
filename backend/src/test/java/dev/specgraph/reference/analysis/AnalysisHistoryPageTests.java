package dev.specgraph.reference.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("VFY-HISTORY-001")
final class AnalysisHistoryPageTests {

    @Test
    void maximumPageIndexCannotOverflowNextPageMetadata() {
        var page = new AnalysisHistoryPage(List.of(), Integer.MAX_VALUE, 1, Integer.MAX_VALUE);

        assertThat(page.hasPrevious()).isTrue();
        assertThat(page.hasNext()).isFalse();
    }
}
