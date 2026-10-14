package io.agenticawithakka.application.ports;

import io.agenticawithakka.domain.contracts.EvidencePassage;
import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Hybrid knowledge search. Implementations apply project/source filtering and a current
 * authorization check before returning any passage; restricted content is omitted, not redacted.
 */
public interface SearchGateway {
    AdapterCapabilities capabilities();

    CompletionStage<List<EvidencePassage>> search(SearchQuery query);
}
