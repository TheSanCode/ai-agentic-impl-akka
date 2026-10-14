package io.agenticawithakka.retrieval;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "retrieval.synthetic", ignoreUnknownFields = false)
public record SyntheticRetrievalProperties(
        @NotBlank @Pattern(regexp = "agenticawithakka_synthetic") String databaseName,
        @NotBlank @Pattern(regexp = "agentica_synthetic") String schemaName,
        @NotNull UUID ownerId,
        @NotBlank @Pattern(regexp = "http://(?:localhost|127\\.0\\.0\\.1):[0-9]{1,5}") String ollamaBaseUrl,
        @NotBlank @Pattern(regexp = "nomic-embed-text:latest") String embeddingModel,
        @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String embeddingModelDigest,
        @Min(1) @Max(16_000) int embeddingDimensions) {}
