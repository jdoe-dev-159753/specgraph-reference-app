package dev.specgraph.reference.identity.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.specgraph.reference.identity.OperatorContext;
import dev.specgraph.reference.identity.OperatorContextPort;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Browser session discovery adapter exposing explicit authentication state and the CSRF token needed
 * for subsequent state-changing requests. It does not authenticate by itself; the security filter
 * chain owns login and logout processing.
 */
@RestController
@Profile("r4 | r4-auth")
@RequestMapping("/api/session")
final class OperatorSessionHttpAdapter {
    private final OperatorContextPort operatorContext;

    OperatorSessionHttpAdapter(OperatorContextPort operatorContext) {
        this.operatorContext = operatorContext;
    }

    /** Projects the current sealed operator state together with the token required for the next write. */
    @GetMapping
    SessionResponse session(CsrfToken csrfToken) {
        CsrfView csrf = CsrfView.from(csrfToken);
        return switch (operatorContext.current()) {
            case OperatorContext.Authenticated authenticated ->
                    new AuthenticatedSession(authenticated.operatorId().value(), csrf);
            case OperatorContext.Unauthenticated ignored -> new UnauthenticatedSession(csrf);
        };
    }

    /** Closed transport response so authentication state is never inferred from nullable fields. */
    sealed interface SessionResponse permits AuthenticatedSession, UnauthenticatedSession {
        /** Explicit wire discriminator for the sealed session response. */
        enum State { AUTHENTICATED, UNAUTHENTICATED }

        CsrfView csrf();

        /** Serializes an explicit discriminator so clients never infer state from nullable fields. */
        @JsonProperty("state")
        default State state() {
            return switch (this) {
                case AuthenticatedSession ignored -> State.AUTHENTICATED;
                case UnauthenticatedSession ignored -> State.UNAUTHENTICATED;
            };
        }
    }

    /** Session view carrying the accountable operator identity. */
    record AuthenticatedSession(String operatorId, CsrfView csrf) implements SessionResponse {
        AuthenticatedSession {
            Objects.requireNonNull(operatorId, "operatorId");
            Objects.requireNonNull(csrf, "csrf");
        }
    }

    /** Anonymous session view that still exposes a CSRF token for the login request. */
    record UnauthenticatedSession(CsrfView csrf) implements SessionResponse {
        UnauthenticatedSession {
            Objects.requireNonNull(csrf, "csrf");
        }
    }

    /** Framework-neutral transport projection of the active CSRF token contract. */
    record CsrfView(String headerName, String parameterName, String token) {
        CsrfView {
            Objects.requireNonNull(headerName, "headerName");
            Objects.requireNonNull(parameterName, "parameterName");
            Objects.requireNonNull(token, "token");
        }

        static CsrfView from(CsrfToken token) {
            return new CsrfView(token.getHeaderName(), token.getParameterName(), token.getToken());
        }
    }
}
