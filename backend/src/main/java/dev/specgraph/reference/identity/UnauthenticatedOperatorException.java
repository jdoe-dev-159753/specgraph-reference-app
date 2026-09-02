package dev.specgraph.reference.identity;

public final class UnauthenticatedOperatorException extends RuntimeException {
    public UnauthenticatedOperatorException() {
        super("an authenticated operator is required");
    }
}
