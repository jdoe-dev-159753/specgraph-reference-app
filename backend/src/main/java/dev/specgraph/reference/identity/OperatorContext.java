package dev.specgraph.reference.identity;

import java.util.Objects;

/**
 * Project-owned authenticated-session state. The concrete record variant is the discriminant.
 */
public sealed interface OperatorContext permits OperatorContext.Authenticated, OperatorContext.Unauthenticated {

    /** Context variant carrying the operator accountable for the current action. */
    record Authenticated(OperatorId operatorId) implements OperatorContext {
        public Authenticated {
            Objects.requireNonNull(operatorId, "operatorId");
        }
    }

    /** Explicit absence of an authenticated operator; never represented by a nullable identity. */
    record Unauthenticated() implements OperatorContext {}
}
