package dev.specgraph.reference.analysis;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Resolves one configured Stage-3 strategy without leaking provider/runtime types into application code. */
final class AnalysisBackendFactory {
    private final Map<AnalysisBackendId, Supplier<? extends AnalysisModelPort>> backends;

    AnalysisBackendFactory(Map<AnalysisBackendId, Supplier<? extends AnalysisModelPort>> backends) {
        Objects.requireNonNull(backends, "backends");
        var copy = new EnumMap<AnalysisBackendId, Supplier<? extends AnalysisModelPort>>(AnalysisBackendId.class);
        copy.putAll(backends);
        this.backends = Map.copyOf(copy);
    }

    /** Resolves the selected backend lazily so unselected provider infrastructure need not exist. */
    AnalysisModelPort resolve(AnalysisBackendId backend) {
        Objects.requireNonNull(backend, "backend");
        Supplier<? extends AnalysisModelPort> supplier = backends.get(backend);
        if (supplier == null) {
            throw new IllegalStateException("Analysis backend " + backend + " is not available in this application revision");
        }
        AnalysisModelPort resolved = supplier.get();
        if (resolved == null) {
            throw new IllegalStateException("Analysis backend " + backend + " is selected but its adapter is unavailable");
        }
        return resolved;
    }
}
