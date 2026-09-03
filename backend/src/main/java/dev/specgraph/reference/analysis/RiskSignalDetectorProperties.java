package dev.specgraph.reference.analysis;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("specgraph.analysis")
record RiskSignalDetectorProperties(List<RiskSignalDetectorId> detectors) {
    RiskSignalDetectorProperties {
        detectors = detectors == null ? null : List.copyOf(detectors);
    }

    boolean hasExplicitSelection() {
        return detectors != null;
    }

    List<RiskSignalDetectorId> configuredDetectors() {
        return detectors == null ? List.of() : detectors;
    }
}
