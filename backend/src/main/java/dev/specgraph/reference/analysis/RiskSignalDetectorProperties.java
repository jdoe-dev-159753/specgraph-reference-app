package dev.specgraph.reference.analysis;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Spring-bound Stage-1 detector selection. An explicitly bound typed selection is authoritative;
 * legacy detector profiles are consulted only when this property is absent.
 */
@ConfigurationProperties("specgraph.analysis")
record RiskSignalDetectorProperties(List<RiskSignalDetectorId> detectors) {
    RiskSignalDetectorProperties {
        // null means the typed property is absent and compatibility profiles/default may apply;
        // an explicit empty list is preserved so the factory can reject it fail-closed.
        detectors = detectors == null ? null : List.copyOf(detectors);
    }

    boolean hasExplicitSelection() {
        return detectors != null;
    }

    List<RiskSignalDetectorId> configuredDetectors() {
        return detectors == null ? List.of() : detectors;
    }
}
