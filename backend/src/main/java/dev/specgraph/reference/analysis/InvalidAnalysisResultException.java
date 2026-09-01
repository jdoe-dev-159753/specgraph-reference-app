package dev.specgraph.reference.analysis;

/** Raised when model output violates the project-owned structured analysis contract. */
final class InvalidAnalysisResultException extends IllegalArgumentException {
    InvalidAnalysisResultException(String message) {
        super(message);
    }
}
