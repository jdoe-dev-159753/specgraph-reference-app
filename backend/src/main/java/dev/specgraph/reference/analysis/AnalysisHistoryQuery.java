package dev.specgraph.reference.analysis;

/** Application-owned pagination contract for operator-facing analysis history. */
public record AnalysisHistoryQuery(int page, int pageSize) {
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    /** Normalizes and bounds pagination before an adapter computes its database offset. */
    public AnalysisHistoryQuery {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize must be between 1 and " + MAX_PAGE_SIZE);
        }
        if ((long) page * pageSize > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("page and pageSize exceed the supported pagination offset");
        }
    }

    public static AnalysisHistoryQuery firstPage() {
        return new AnalysisHistoryQuery(0, DEFAULT_PAGE_SIZE);
    }

    public long offset() {
        return Math.multiplyExact((long) page, pageSize);
    }
}
