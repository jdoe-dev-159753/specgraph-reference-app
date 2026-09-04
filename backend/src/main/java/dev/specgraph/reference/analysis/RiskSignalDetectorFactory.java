package dev.specgraph.reference.analysis;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/** Resolves typed Stage-1 detector selections without leaking implementation classes downstream. */
final class RiskSignalDetectorFactory {
    private static final int MAX_DETECTORS = 8;

    private final Map<RiskSignalDetectorId, Supplier<? extends RiskSignalDetectorPort>> registry;

    RiskSignalDetectorFactory(
            Map<RiskSignalDetectorId, Supplier<? extends RiskSignalDetectorPort>> registry) {
        Objects.requireNonNull(registry, "registry");
        EnumMap<RiskSignalDetectorId, Supplier<? extends RiskSignalDetectorPort>> copy =
                new EnumMap<>(RiskSignalDetectorId.class);
        registry.forEach((id, provider) -> copy.put(
                Objects.requireNonNull(id, "detector id"),
                new MemoizedDetectorProvider(id, provider)));
        this.registry = Map.copyOf(copy);
    }

    RiskSignalDetectorPort resolve(List<RiskSignalDetectorId> selection) {
        Objects.requireNonNull(selection, "selection");
        if (selection.isEmpty()) {
            throw new IllegalArgumentException("At least one detector must be selected");
        }
        if (selection.size() > MAX_DETECTORS) {
            throw new IllegalArgumentException("Detector selection exceeds bounded maximum of " + MAX_DETECTORS);
        }

        Set<RiskSignalDetectorId> unique = new HashSet<>();
        for (RiskSignalDetectorId id : selection) {
            if (!unique.add(Objects.requireNonNull(id, "detector id"))) {
                throw new IllegalArgumentException("Duplicate detector selection: " + id);
            }
        }
        if (selection.size() > 1 && unique.contains(RiskSignalDetectorId.NO_OP)) {
            throw new IllegalArgumentException("NO_OP cannot be combined with concrete detector leaves");
        }

        List<RiskSignalDetectorPort> children = new ArrayList<>(selection.size());
        for (RiskSignalDetectorId id : selection) {
            Supplier<? extends RiskSignalDetectorPort> provider = registry.get(id);
            if (provider == null) {
                throw new IllegalArgumentException("No registered detector for " + id);
            }
            children.add(provider.get());
        }

        if (children.size() == 1) {
            return children.get(0);
        }
        return new CompositeRiskSignalDetector(children);
    }

    private static final class MemoizedDetectorProvider implements Supplier<RiskSignalDetectorPort> {
        private final RiskSignalDetectorId id;
        private final Supplier<? extends RiskSignalDetectorPort> delegate;
        private volatile RiskSignalDetectorPort detector;

        private MemoizedDetectorProvider(
                RiskSignalDetectorId id,
                Supplier<? extends RiskSignalDetectorPort> delegate) {
            this.id = id;
            this.delegate = Objects.requireNonNull(delegate, "detector provider");
        }

        @Override
        public RiskSignalDetectorPort get() {
            RiskSignalDetectorPort resolved = detector;
            if (resolved == null) {
                synchronized (this) {
                    resolved = detector;
                    if (resolved == null) {
                        resolved = Objects.requireNonNull(
                                delegate.get(),
                                () -> "Registered detector provider returned null for " + id);
                        detector = resolved;
                    }
                }
            }
            return resolved;
        }
    }
}
