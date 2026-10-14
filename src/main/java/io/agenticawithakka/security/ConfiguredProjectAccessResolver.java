package io.agenticawithakka.security;

import io.agenticawithakka.config.ServiceProperties;
import io.agenticawithakka.domain.contracts.ContractValidation;
import io.agenticawithakka.domain.contracts.ProjectId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Local-lab, server-configured membership table. Missing mappings are denied. */
public final class ConfiguredProjectAccessResolver implements ProjectAccessResolver {
    private static final int MAX_PRINCIPALS = 256;
    private final Map<OidcPrincipalKey, Map<ProjectId, MockIdentityContextStore.ProjectAccess>> accessByPrincipal;

    public ConfiguredProjectAccessResolver(ServiceProperties.Security security) {
        ContractValidation.required(security, "security");
        if (security.principals().size() > MAX_PRINCIPALS) {
            throw new IllegalArgumentException("configured principals exceeds " + MAX_PRINCIPALS);
        }
        var entries = new HashMap<OidcPrincipalKey, Map<ProjectId, MockIdentityContextStore.ProjectAccess>>();
        for (ServiceProperties.PrincipalAccess principal : security.principals()) {
            ContractValidation.required(principal, "principal");
            var key = new OidcPrincipalKey(principal.issuer(), principal.subject());
            if (principal.projects().isEmpty() || principal.projects().size() > 32) {
                throw new IllegalArgumentException("each configured principal must have 1 to 32 projects");
            }
            var projects = new HashMap<ProjectId, MockIdentityContextStore.ProjectAccess>();
            principal.projects().forEach((projectValue, access) -> {
                var project = new ProjectId(projectValue);
                ContractValidation.required(access, "projectAccess");
                var projectAccess = new MockIdentityContextStore.ProjectAccess(
                        access.resources(), access.sources());
                if (projects.putIfAbsent(project, projectAccess) != null) {
                    throw new IllegalArgumentException("duplicate project mapping for configured principal");
                }
            });
            if (entries.putIfAbsent(key, Map.copyOf(projects)) != null) {
                throw new IllegalArgumentException("duplicate configured OIDC principal mapping");
            }
        }
        this.accessByPrincipal = Map.copyOf(entries);
    }

    @Override
    public Optional<Map<ProjectId, MockIdentityContextStore.ProjectAccess>> resolve(OidcPrincipalKey principal) {
        ContractValidation.required(principal, "principal");
        return Optional.ofNullable(accessByPrincipal.get(principal));
    }
}
