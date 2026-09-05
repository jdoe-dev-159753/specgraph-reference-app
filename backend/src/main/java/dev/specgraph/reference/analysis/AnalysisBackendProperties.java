package dev.specgraph.reference.analysis;

import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed configuration owning the explicit Stage-3 backend selection. */
@ConfigurationProperties("specgraph.analysis")
record AnalysisBackendProperties(AnalysisBackendId backend) {
    AnalysisBackendProperties {
        Objects.requireNonNull(backend, "backend");
    }
}
