package io.agenticawithakka.retrieval;

import io.agenticawithakka.application.ports.AdapterCapabilities;
import io.agenticawithakka.application.ports.EmbeddingGateway;
import io.agenticawithakka.application.ports.EmbeddingVector;
import io.agenticawithakka.application.ports.PortException;
import io.agenticawithakka.domain.contracts.ContractValidation;
import io.agenticawithakka.domain.contracts.ErrorCode;
import io.agenticawithakka.domain.contracts.EvidencePassage;
import io.agenticawithakka.retrieval.SyntheticRetrievalProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import tools.jackson.databind.json.JsonMapper;

/** Digest-pinned local Ollama embedding adapter for synthetic retrieval. */
public final class OllamaEmbeddingGateway implements EmbeddingGateway {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final String CAPABILITIES_PATH = "/api/show";
    private static final String TAGS_PATH = "/api/tags";
    private static final String EMBEDDINGS_PATH = "/api/embeddings";

    private final SyntheticRetrievalProperties properties;
    private final JsonMapper json;
    private final HttpClient http;
    private final Executor executor;
    private final String modelId;

    public OllamaEmbeddingGateway(
            SyntheticRetrievalProperties properties, JsonMapper json, Executor executor) {
        this.properties = ContractValidation.required(properties, "properties");
        this.json = ContractValidation.required(json, "json");
        this.executor = ContractValidation.required(executor, "executor");
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.modelId = properties.embeddingModel() + ":" + properties.embeddingModelDigest();
        verifyInstalledModel();
    }

    @Override
    public AdapterCapabilities capabilities() {
        return new AdapterCapabilities(
                "ollama-embedding",
                Set.of("local-inference", "digest-pinned-model", "real-embeddings"),
                List.of("Synthetic local retrieval only; chat and agent execution are not configured."));
    }

    @Override
    public CompletionStage<EmbeddingVector> embed(String text) {
        ContractValidation.text(text, "embedding.text", EvidencePassage.MAX_LENGTH);
        return CompletableFuture.supplyAsync(() -> {
            var response = post(EMBEDDINGS_PATH, new EmbeddingRequest(properties.embeddingModel(), text),
                    EmbeddingResponse.class);
            if (response.embedding() == null || response.embedding().size() != properties.embeddingDimensions()) {
                throw new PortException(
                        ErrorCode.DEPENDENCY_UNAVAILABLE, "embedding model returned an unexpected vector shape");
            }
            return new EmbeddingVector(modelId, response.embedding());
        }, executor);
    }

    private void verifyInstalledModel() {
        var tags = get(TAGS_PATH, TagsResponse.class);
        var matchingModel = tags.models() == null
                ? null
                : tags.models().stream()
                        .filter(model -> properties.embeddingModel().equals(model.name()))
                        .findFirst()
                        .orElse(null);
        if (matchingModel == null || !properties.embeddingModelDigest().equals(matchingModel.digest())) {
            throw new IllegalStateException("Configured Ollama embedding model is absent or its digest does not match");
        }
        var show = post(CAPABILITIES_PATH, new ShowRequest(properties.embeddingModel()), ModelDetails.class);
        if (show.capabilities() == null || !show.capabilities().contains("embedding")) {
            throw new IllegalStateException("Configured Ollama model does not provide embeddings");
        }
    }

    private <T> T get(String path, Class<T> responseType) {
        return send(HttpRequest.newBuilder(endpoint(path)).timeout(REQUEST_TIMEOUT).GET().build(), responseType);
    }

    private <T> T post(String path, Object body, Class<T> responseType) {
        String serialized = json.writeValueAsString(body);
        var request = HttpRequest.newBuilder(endpoint(path))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(serialized))
                .build();
        return send(request, responseType);
    }

    private <T> T send(HttpRequest request, Class<T> responseType) {
        try {
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new PortException(ErrorCode.DEPENDENCY_UNAVAILABLE, "Ollama embedding service is unavailable");
            }
            return json.readValue(response.body(), responseType);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new PortException(ErrorCode.DEPENDENCY_UNAVAILABLE, "Ollama request was interrupted");
        } catch (IOException failure) {
            throw new PortException(ErrorCode.DEPENDENCY_UNAVAILABLE, "Ollama response could not be read");
        }
    }

    private URI endpoint(String path) {
        return URI.create(properties.ollamaBaseUrl() + path);
    }

    private record TagsResponse(List<ModelTag> models) {}

    private record ModelTag(String name, String digest) {}

    private record ModelDetails(List<String> capabilities) {}

    private record ShowRequest(String name) {}

    private record EmbeddingRequest(String model, String prompt) {}

    private record EmbeddingResponse(List<Float> embedding) {}
}
