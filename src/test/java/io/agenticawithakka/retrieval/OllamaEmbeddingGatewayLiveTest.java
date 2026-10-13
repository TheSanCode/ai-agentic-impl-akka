package io.agenticawithakka.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import tools.jackson.databind.json.JsonMapper;

class OllamaEmbeddingGatewayLiveTest {
    private static final String MODEL = "nomic-embed-text:latest";
    private static final String DIGEST = "0a109f422b47e3a30ba2b10eca18548e944e8a23073ee3f3e947efcf3c45e59f";

    @Test
    @EnabledIfSystemProperty(named = "ollama.embedding.live", matches = "true")
    void producesPinnedRealOllamaEmbedding() {
        var executor = Executors.newSingleThreadExecutor();
        try {
            var properties = new SyntheticRetrievalProperties(
                    "agenticawithakka_synthetic",
                    "agentica_synthetic",
                    java.util.UUID.fromString("151d4fa3-43a7-4821-a4ae-16513812569e"),
                    "http://127.0.0.1:11434",
                    MODEL,
                    DIGEST,
                    768);
            var gateway = new OllamaEmbeddingGateway(properties, JsonMapper.builder().build(), executor);

            var embedding = gateway.embed("Synthetic Alpha runbook about connection-pool timeouts.")
                    .toCompletableFuture()
                    .join();

            assertThat(embedding.modelId()).isEqualTo(MODEL + ":" + DIGEST);
            assertThat(embedding.dimensions()).isEqualTo(768);
            assertThat(embedding.values()).anyMatch(value -> value != 0.0f);
        } finally {
            executor.shutdownNow();
        }
    }
}
