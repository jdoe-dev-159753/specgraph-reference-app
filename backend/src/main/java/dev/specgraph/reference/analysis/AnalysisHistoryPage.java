package dev.specgraph.reference.analysis;

import java.util.List;
import java.util.Objects;

/** Bounded operator-facing page of retained analyses. */
public record AnalysisHistoryPage(
        List<AnalysisHistoryEntry> entries,
        int page,
        int pageSize,
        long totalEntries) {
    /** Preserves bounded paging metadata even for an empty page beyond the available history. */
    public AnalysisHistoryPage {
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        if (page < 0 || pageSize < 1 || totalEntries < 0) {
            throw new IllegalArgumentException("history page metadata is invalid");
        }
        if (entries.size() > pageSize) {
            throw new IllegalArgumentException("history entries exceed pageSize");
        }
    }

    public long totalPages() {
        return totalEntries == 0 ? 0 : (totalEntries + pageSize - 1) / pageSize;
    }

    public boolean hasPrevious() {
        return page > 0;
    }

    public boolean hasNext() {
        return ((long) page + 1) * pageSize < totalEntries;
    }
}
