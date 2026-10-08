package io.agenticawithakka.application.ports;

import java.util.concurrent.CompletionStage;

/** Provider-neutral model access; Spring AI or other clients stay inside adapters. */
public interface ModelGateway {
    AdapterCapabilities capabilities();

    CompletionStage<ModelResponse> complete(ModelRequest request);
}
