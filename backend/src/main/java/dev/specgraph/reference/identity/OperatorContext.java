package dev.specgraph.reference.identity;

import java.util.Objects;

/**
 * Project-owned authenticated-session state. The concrete record variant is the discriminant.
 */
public sealed interface OperatorContext permits OperatorContext.Authenticated, OperatorContext.Unauthenticated {

    record Authenticated(OperatorId operatorId) implements OperatorContext {
        public Authenticated {
            Objects.requireNonNull(operatorId, "operatorId");
        }
    }

    record Unauthenticated() implements OperatorContext {}
}
