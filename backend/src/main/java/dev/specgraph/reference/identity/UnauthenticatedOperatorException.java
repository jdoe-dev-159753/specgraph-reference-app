package dev.specgraph.reference.identity;

/** Raised when an application operation requiring operator accountability has no authenticated context. */
public final class UnauthenticatedOperatorException extends RuntimeException {
    public UnauthenticatedOperatorException() {
        super("an authenticated operator is required");
    }
}
