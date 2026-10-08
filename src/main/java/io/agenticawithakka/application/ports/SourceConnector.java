package io.agenticawithakka.application.ports;

import io.agenticawithakka.domain.contracts.EvidencePassage;
import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Read-only access to one enterprise or mock source using the caller's delegated credential. The
 * source independently validates the credential's audience and scopes. Returned content is
 * untrusted evidence. Protected writes use a separate ActionExecutor port (not yet defined).
 */
public interface SourceConnector {
    String sourceId();

    AdapterCapabilities capabilities();

    CompletionStage<List<EvidencePassage>> read(SourceReadRequest request, DelegatedCredential credential);
}
