package dev.specgraph.reference.identity;

/**
 * Inbound-neutral boundary exposing the operator identity associated with the current execution.
 * Implementations adapt a transport or security context without leaking framework authentication
 * types into application contracts.
 */
public interface OperatorContextPort {
    /** Returns the explicit authenticated or unauthenticated context variant. */
    OperatorContext current();

    /**
     * Returns the current operator identity.
     *
     * @throws UnauthenticatedOperatorException when no authenticated operator is present
     */
    default OperatorId requireAuthenticated() {
        return switch (current()) {
            case OperatorContext.Authenticated authenticated -> authenticated.operatorId();
            case OperatorContext.Unauthenticated ignored -> throw new UnauthenticatedOperatorException();
        };
    }
}
