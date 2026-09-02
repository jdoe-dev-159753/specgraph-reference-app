package dev.specgraph.reference.identity;

public interface OperatorContextPort {
    OperatorContext current();

    default OperatorId requireAuthenticated() {
        return switch (current()) {
            case OperatorContext.Authenticated authenticated -> authenticated.operatorId();
            case OperatorContext.Unauthenticated ignored -> throw new UnauthenticatedOperatorException();
        };
    }
}
