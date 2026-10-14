package io.agenticawithakka.application.ports;

import java.util.concurrent.CompletionStage;

/**
 * Obtains user-delegated (on-behalf-of) credentials. When delegation is unsupported or fails, the
 * stage completes with {@link PortException} ({@code UNSUPPORTED}, {@code DENIED} or
 * {@code AUTHENTICATION_REQUIRED}); implementations must never fall back to shared credentials.
 */
public interface DelegatedTokenProvider {
    AdapterCapabilities capabilities();

    CompletionStage<DelegatedCredential> acquire(DelegationRequest request);
}
