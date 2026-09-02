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

@RestController
@Profile("r4 | r4-auth")
@RequestMapping("/api/session")
final class OperatorSessionHttpAdapter {
    private final OperatorContextPort operatorContext;

    OperatorSessionHttpAdapter(OperatorContextPort operatorContext) {
        this.operatorContext = operatorContext;
    }

    @GetMapping
    SessionResponse session(CsrfToken csrfToken) {
        CsrfView csrf = CsrfView.from(csrfToken);
        return switch (operatorContext.current()) {
            case OperatorContext.Authenticated authenticated ->
                    new AuthenticatedSession(authenticated.operatorId().value(), csrf);
            case OperatorContext.Unauthenticated ignored -> new UnauthenticatedSession(csrf);
        };
    }

    sealed interface SessionResponse permits AuthenticatedSession, UnauthenticatedSession {
        enum State { AUTHENTICATED, UNAUTHENTICATED }

        CsrfView csrf();

        @JsonProperty("state")
        default State state() {
            return switch (this) {
                case AuthenticatedSession ignored -> State.AUTHENTICATED;
                case UnauthenticatedSession ignored -> State.UNAUTHENTICATED;
            };
        }
    }

    record AuthenticatedSession(String operatorId, CsrfView csrf) implements SessionResponse {
        AuthenticatedSession {
            Objects.requireNonNull(operatorId, "operatorId");
            Objects.requireNonNull(csrf, "csrf");
        }
    }

    record UnauthenticatedSession(CsrfView csrf) implements SessionResponse {
        UnauthenticatedSession {
            Objects.requireNonNull(csrf, "csrf");
        }
    }

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
