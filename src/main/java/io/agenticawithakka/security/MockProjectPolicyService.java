package io.agenticawithakka.security;

import io.agenticawithakka.application.ports.AdapterCapabilities;
import io.agenticawithakka.application.ports.PolicyAction;
import io.agenticawithakka.application.ports.PolicyDecision;
import io.agenticawithakka.application.ports.PolicyDecisionService;
import io.agenticawithakka.application.ports.PolicyRequest;
import io.agenticawithakka.domain.contracts.ContractValidation;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Deny-first project and source policy for the local lab; application roles never imply source access. */
public final class MockProjectPolicyService implements PolicyDecisionService {
    private static final AdapterCapabilities CAPABILITIES = new AdapterCapabilities(
            "mock-project-policy",
            Set.of("project-membership", "resource-grants", "source-grants", "deny-by-default"),
            List.of("In-memory policy only; not suitable for production or distributed deployments."));
    private final MockIdentityContextStore identities;

    public MockProjectPolicyService(MockIdentityContextStore identities) {
        this.identities = ContractValidation.required(identities, "identities");
    }

    @Override
    public AdapterCapabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    public CompletionStage<PolicyDecision> decide(PolicyRequest request) {
        ContractValidation.required(request, "request");
        var identity = identities.resolve(request.identityContextRef());
        if (identity.isEmpty()) {
            return completed(PolicyDecision.deny("identity.unavailable"));
        }
        var context = identity.get();
        if (!context.belongsTo(request.projectId())) {
            return completed(PolicyDecision.deny("project.notMember"));
        }

        return switch (request.action()) {
            case READ_EVIDENCE -> completed(canReadEvidence(context, request)
                    ? PolicyDecision.permit()
                    : PolicyDecision.deny("evidence.denied"));
            case START_EXECUTION, READ_EXECUTION, CANCEL_EXECUTION, DISCOVER_TOOL, INVOKE_TOOL ->
                completed(PolicyDecision.permit());
        };
    }

    private static boolean canReadEvidence(
            MockIdentityContextStore.IdentityContext identity, PolicyRequest request) {
        String resourceId = request.resourceId();
        if (resourceId.startsWith("arg:")) {
            String[] parts = resourceId.split(":", 3);
            if (parts.length != 3) {
                return false;
            }
            return identity.mayAccessResource(request.projectId(), parts[1] + ":" + parts[2]);
        }
        return identity.mayAccessSource(request.projectId(), resourceId);
    }

    private static CompletionStage<PolicyDecision> completed(PolicyDecision decision) {
        return CompletableFuture.completedFuture(decision);
    }
}
