package io.agenticawithakka.security;

import io.agenticawithakka.application.ports.PortException;
import io.agenticawithakka.domain.contracts.ErrorCode;
import io.agenticawithakka.domain.contracts.IdentityContextRef;
import io.agenticawithakka.domain.contracts.ProjectId;
import io.agenticawithakka.domain.contracts.ContractValidation;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Maps a Spring Security-validated JWT principal to a server-held identity context. The principal's
 * requested project is never used to create grants; configured membership is looked up by (iss, sub).
 */
public final class AuthenticatedIdentityContextResolver {
    private final MockIdentityContextStore contexts;
    private final ProjectAccessResolver accessResolver;
    private final Clock clock;
    private final Map<OidcPrincipalKey, IdentityContextRef> references = new ConcurrentHashMap<>();

    public AuthenticatedIdentityContextResolver(
            MockIdentityContextStore contexts, ProjectAccessResolver accessResolver, Clock clock) {
        this.contexts = ContractValidation.required(contexts, "contexts");
        this.accessResolver = ContractValidation.required(accessResolver, "accessResolver");
        this.clock = ContractValidation.required(clock, "clock");
    }

    public IdentityContextRef resolve(Authentication authentication) {
        OidcPrincipalKey key = principalKey(authentication);
        var projectAccess = accessResolver.resolve(key)
                .filter(access -> !access.isEmpty())
                .orElseThrow(() ->
                        new PortException(ErrorCode.DENIED, "no active project membership is configured"));

        IdentityContextRef existing = references.get(key);
        if (existing != null && contexts.resolve(existing).isPresent()) {
            return existing;
        }
        IdentityContextRef created =
                contexts.registerAuthenticatedSubject(key.identityStoreKey(), projectAccess, expiresAt(authentication));
        references.put(key, created);
        return created;
    }

    public OidcPrincipalKey principalKey(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)
                || !jwtAuthentication.isAuthenticated()) {
            throw new PortException(ErrorCode.AUTHENTICATION_REQUIRED, "a verified JWT principal is required");
        }
        var jwt = jwtAuthentication.getToken();
        if (jwt.getIssuer() == null || jwt.getSubject() == null || jwt.getExpiresAt() == null) {
            throw new PortException(
                    ErrorCode.AUTHENTICATION_REQUIRED, "verified JWT lacks issuer, subject or expiry");
        }
        return new OidcPrincipalKey(jwt.getIssuer().toString(), jwt.getSubject());
    }

    private static java.time.Instant expiresAt(Authentication authentication) {
        return ((JwtAuthenticationToken) authentication).getToken().getExpiresAt();
    }

    public boolean mayAccessProject(IdentityContextRef identityContextRef, ProjectId projectId) {
        ContractValidation.required(identityContextRef, "identityContextRef");
        ContractValidation.required(projectId, "projectId");
        return contexts.resolve(identityContextRef)
                .map(identity -> identity.belongsTo(projectId))
                .orElse(false);
    }
}
