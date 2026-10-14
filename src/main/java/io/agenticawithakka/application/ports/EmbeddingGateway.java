package io.agenticawithakka.application.ports;

import java.util.concurrent.CompletionStage;

/** Replaceable embedding provider. A returned vector always declares model identity and dimension. */
public interface EmbeddingGateway {
    AdapterCapabilities capabilities();

    CompletionStage<EmbeddingVector> embed(String text);
}
