package io.agenticawithakka.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Public, non-sensitive service identifier used by the process health check. */
@Validated
@ConfigurationProperties(prefix = "app", ignoreUnknownFields = false)
public record ServiceProperties(
        @NotBlank @Size(max = 64) @Pattern(regexp = "[a-z][a-z0-9-]*") String serviceName,
        @DefaultValue Security security) {

    public ServiceProperties {
        security = security == null ? Security.disabled() : security;
    }

    public record Security(
            String issuerUri,
            String jwkSetUri,
            String audience,
            @DefaultValue List<PrincipalAccess> principals) {
        public Security {
            principals = principals == null ? List.of() : List.copyOf(principals);
        }

        static Security disabled() {
            return new Security(null, null, null, List.of());
        }

        public boolean configured() {
            return issuerUri != null && !issuerUri.isBlank()
                    && jwkSetUri != null && !jwkSetUri.isBlank()
                    && audience != null && !audience.isBlank();
        }

        public boolean partiallyConfigured() {
            return (issuerUri != null && !issuerUri.isBlank())
                    || (jwkSetUri != null && !jwkSetUri.isBlank())
                    || (audience != null && !audience.isBlank());
        }
    }

    public record PrincipalAccess(String issuer, String subject, Map<String, ProjectAccess> projects) {
        public PrincipalAccess {
            projects = projects == null ? Map.of() : Map.copyOf(projects);
        }
    }

    public record ProjectAccess(Set<String> resources, Set<String> sources) {
        public ProjectAccess {
            resources = resources == null ? Set.of() : Set.copyOf(resources);
            sources = sources == null ? Set.of() : Set.copyOf(sources);
        }
    }
}
