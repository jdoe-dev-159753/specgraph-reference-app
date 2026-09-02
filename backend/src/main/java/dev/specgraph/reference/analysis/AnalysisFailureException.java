package dev.specgraph.reference.analysis;

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
