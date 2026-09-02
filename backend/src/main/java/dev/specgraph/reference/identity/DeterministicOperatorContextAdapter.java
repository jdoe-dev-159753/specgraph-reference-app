package dev.specgraph.reference.identity;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

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
