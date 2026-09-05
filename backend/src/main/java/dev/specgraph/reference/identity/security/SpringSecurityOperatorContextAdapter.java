package dev.specgraph.reference.identity.security;

import dev.specgraph.reference.identity.OperatorContext;
import dev.specgraph.reference.identity.OperatorContextPort;
import dev.specgraph.reference.identity.OperatorId;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Translates Spring Security authentication into the application-owned operator context.
 * Anonymous tokens are explicitly treated as unauthenticated even when a framework object exists.
 */
@Component
@Profile("r4 | r4-auth")
final class SpringSecurityOperatorContextAdapter implements OperatorContextPort {

    @Override
    public OperatorContext current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return new OperatorContext.Unauthenticated();
        }
        return new OperatorContext.Authenticated(new OperatorId(authentication.getName()));
    }
}
