package dev.specgraph.reference.analysis;

import java.util.Objects;

/**
 * Typed pointer from one generated analysis result to evidence that was actually supplied to the
 * model. This is a reference descriptor, not a claim that the referenced evidence families share
 * semantic authority.
 */
public record AnalysisEvidenceReference(Kind kind, String evidenceIdentity) {
    public AnalysisEvidenceReference {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(evidenceIdentity, "evidenceIdentity");
        if (evidenceIdentity.isBlank()) {
            throw new IllegalArgumentException("evidenceIdentity must not be blank");
        }
    }

    /** Evidence family used to resolve the identity against the supplied model envelope. */
    public enum Kind {
        ACTIVITY,
        SOURCE_RISK,
        DETECTOR_SIGNAL,
        POLICY_RETRIEVAL
    }
}
