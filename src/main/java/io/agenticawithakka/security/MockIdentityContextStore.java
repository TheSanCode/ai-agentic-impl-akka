package io.agenticawithakka.security;

import io.agenticawithakka.domain.contracts.ContractValidation;
import io.agenticawithakka.domain.contracts.IdentityContextRef;
import io.agenticawithakka.domain.contracts.ProjectId;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory local-lab identity context store. Callers may register a subject only after an
 * authentication boundary has verified it; this class itself does not authenticate credentials.
 */
public final class MockIdentityContextStore {
    private static final Duration MAX_LIFETIME = Duration.ofHours(24);
    private final Clock clock;
    private final Duration lifetime;
    private final SecureRandom random = new SecureRandom();
    private final Map<IdentityContextRef, IdentityContext> contexts = new ConcurrentHashMap<>();

    public MockIdentityContextStore(Clock clock, Duration lifetime) {
        this.clock = ContractValidation.required(clock, "clock");
        this.lifetime = ContractValidation.required(lifetime, "lifetime");
        if (lifetime.isZero() || lifetime.isNegative() || lifetime.compareTo(MAX_LIFETIME) > 0) {
            throw new IllegalArgumentException("lifetime must be positive and at most 24 hours");
        }
    }

    /** Creates an opaque server-held reference for a principal already authenticated upstream. */
    public IdentityContextRef registerAuthenticatedSubject(
            String subject, Map<ProjectId, ProjectAccess> projectAccess) {
        ContractValidation.matches(subject, "subject", ContractValidation.REFERENCE);
        ContractValidation.required(projectAccess, "projectAccess");
        if (projectAccess.isEmpty() || projectAccess.size() > 32) {
            throw new IllegalArgumentException("projectAccess must contain between 1 and 32 projects");
        }
        var access = Map.copyOf(projectAccess);
        access.forEach((project, permissions) -> {
            ContractValidation.required(project, "projectAccess.project");
            ContractValidation.required(permissions, "projectAccess.permissions");
        });
        var context = new IdentityContext(subject, access, clock.instant().plus(lifetime));
        IdentityContextRef reference;
        do {
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            reference = new IdentityContextRef(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
        } while (contexts.putIfAbsent(reference, context) != null);
        return reference;
    }

    public Optional<IdentityContext> resolve(IdentityContextRef reference) {
        ContractValidation.required(reference, "reference");
        IdentityContext context = contexts.get(reference);
        if (context == null) {
            return Optional.empty();
        }
        if (!clock.instant().isBefore(context.expiresAt())) {
            contexts.remove(reference, context);
            return Optional.empty();
        }
        return Optional.of(context);
    }

    public void revoke(IdentityContextRef reference) {
        ContractValidation.required(reference, "reference");
        contexts.remove(reference);
    }

    public record ProjectAccess(Set<String> resources, Set<String> sources) {
        public ProjectAccess {
            resources = ContractValidation.set(resources, "resources", 256);
            sources = ContractValidation.set(sources, "sources", 64);
            for (String resource : resources) {
                ContractValidation.matches(resource, "resources", ContractValidation.REFERENCE);
            }
            for (String source : sources) {
                ContractValidation.matches(source, "sources", ContractValidation.REFERENCE);
            }
        }
    }

    public record IdentityContext(
            String subject, Map<ProjectId, ProjectAccess> projectAccess, Instant expiresAt) {
        public IdentityContext {
            ContractValidation.matches(subject, "subject", ContractValidation.REFERENCE);
            projectAccess = Map.copyOf(projectAccess);
            ContractValidation.required(expiresAt, "expiresAt");
        }

        public boolean belongsTo(ProjectId projectId) {
            return projectAccess.containsKey(projectId);
        }

        public boolean mayAccessResource(ProjectId projectId, String resourceId) {
            ProjectAccess access = projectAccess.get(projectId);
            return access != null && access.resources().contains(resourceId);
        }

        public boolean mayAccessSource(ProjectId projectId, String sourceId) {
            ProjectAccess access = projectAccess.get(projectId);
            return access != null && access.sources().contains(sourceId);
        }
    }
}
