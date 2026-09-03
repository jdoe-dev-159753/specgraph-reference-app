package dev.specgraph.reference.analysis;

import dev.specgraph.reference.customer.CustomerSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Bounded fail-fast GoF Composite for Stage-1 detector leaves. */
final class CompositeRiskSignalDetector implements RiskSignalDetectorPort {
    private final List<RiskSignalDetectorPort> children;

    CompositeRiskSignalDetector(List<RiskSignalDetectorPort> children) {
        Objects.requireNonNull(children, "children");
        if (children.size() < 2) {
            throw new IllegalArgumentException("Composite requires at least two detector children");
        }
        this.children = List.copyOf(children);
    }

    @Override
    public List<RiskSignalEvidence> detect(CustomerSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        List<RiskSignalEvidence> evidence = new ArrayList<>();
        for (RiskSignalDetectorPort child : children) {
            evidence.addAll(List.copyOf(child.detect(snapshot)));
        }
        return List.copyOf(evidence);
    }
}
