package dev.specgraph.reference.analysis;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Resolves typed Stage-1 detector selections without leaking implementation classes downstream. */
final class RiskSignalDetectorFactory {
    private static final int MAX_DETECTORS = 8;

    private final Map<RiskSignalDetectorId, RiskSignalDetectorPort> registry;

    RiskSignalDetectorFactory(Map<RiskSignalDetectorId, RiskSignalDetectorPort> registry) {
        Objects.requireNonNull(registry, "registry");
        EnumMap<RiskSignalDetectorId, RiskSignalDetectorPort> copy = new EnumMap<>(RiskSignalDetectorId.class);
        copy.putAll(registry);
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
            RiskSignalDetectorPort detector = registry.get(id);
            if (detector == null) {
                throw new IllegalArgumentException("No registered detector for " + id);
            }
            children.add(detector);
        }

        if (children.size() == 1) {
            return children.get(0);
        }
        return new CompositeRiskSignalDetector(children);
    }
}
