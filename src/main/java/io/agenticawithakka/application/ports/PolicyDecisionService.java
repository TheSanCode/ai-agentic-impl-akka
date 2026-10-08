package io.agenticawithakka.application.ports;

import java.util.concurrent.CompletionStage;

/** Decides access before any tool, source or evidence operation runs. Unknown actions are denied. */
public interface PolicyDecisionService {
    AdapterCapabilities capabilities();

    CompletionStage<PolicyDecision> decide(PolicyRequest request);
}
