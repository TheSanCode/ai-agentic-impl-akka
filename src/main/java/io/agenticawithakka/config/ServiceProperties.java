package io.agenticawithakka.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Public, non-sensitive service identifier used by the process health check. */
@Validated
@ConfigurationProperties(prefix = "app", ignoreUnknownFields = false)
public record ServiceProperties(
        @NotBlank @Size(max = 64) @Pattern(regexp = "[a-z][a-z0-9-]*") String serviceName) {
}
