package io.agenticawithakka.security;

import io.agenticawithakka.domain.contracts.ProjectId;
import java.util.Map;
import java.util.Optional;

/** Resolves current server-owned project, resource and source grants for a verified OIDC principal. */
public interface ProjectAccessResolver {
    Optional<Map<ProjectId, MockIdentityContextStore.ProjectAccess>> resolve(OidcPrincipalKey principal);
}
