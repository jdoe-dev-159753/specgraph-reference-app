package dev.specgraph.reference.analysis;

/**
 * Application-level failure raised by the analysis pipeline after adapter-specific failures have
 * been translated into a stable reason.
 *
 * <p>Inbound adapters may map {@link Reason} values to transport responses. The message is an
 * operator-facing diagnostic and must not be treated as a durable machine-readable discriminator.
 */
public final class AnalysisFailureException extends RuntimeException {
    private final Reason reason;

    public AnalysisFailureException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public AnalysisFailureException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    /** Stable application taxonomy used by inbound adapters when translating a pipeline failure. */
    public enum Reason {
        CUSTOMER_NOT_FOUND,
        DETECTOR_FAILURE,
        INSUFFICIENT_GROUNDING,
        GROUNDING_FAILURE,
        MODEL_FAILURE,
        INVALID_RESULT,
        PERSISTENCE_FAILURE
    }
}
