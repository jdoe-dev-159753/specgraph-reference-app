package dev.specgraph.reference.identity;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Offline identity adapter that attributes baseline analyses to one explicit synthetic operator.
 * Security-enabled profiles replace it; it is not evidence of authentication.
 */
@Component
@Profile("!r4 & !r4-auth")
final class DeterministicOperatorContextAdapter implements OperatorContextPort {
    private static final OperatorContext CONTEXT =
            new OperatorContext.Authenticated(new OperatorId("r3-demo-operator"));

    @Override
    public OperatorContext current() {
        return CONTEXT;
    }
}
