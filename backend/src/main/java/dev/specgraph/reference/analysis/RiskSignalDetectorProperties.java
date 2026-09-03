package dev.specgraph.reference.analysis;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("specgraph.analysis")
record RiskSignalDetectorProperties(List<RiskSignalDetectorId> detectors) {
    RiskSignalDetectorProperties {
        detectors = detectors == null ? List.of() : List.copyOf(detectors);
    }

    boolean hasExplicitSelection() {
        return !detectors.isEmpty();
    }
}
