package dev.specgraph.reference.analysis;

import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("specgraph.analysis")
record AnalysisBackendProperties(AnalysisBackendId backend) {
    AnalysisBackendProperties {
        Objects.requireNonNull(backend, "backend");
    }
}
