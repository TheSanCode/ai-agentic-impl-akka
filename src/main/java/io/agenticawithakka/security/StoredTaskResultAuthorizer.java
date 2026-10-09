package io.agenticawithakka.security;

import io.agenticawithakka.application.ports.PolicyAction;
import io.agenticawithakka.application.ports.PolicyDecision;
import io.agenticawithakka.application.ports.PolicyDecisionService;
import io.agenticawithakka.application.ports.PolicyRequest;
import io.agenticawithakka.application.ports.PortException;
import io.agenticawithakka.domain.contracts.ContractValidation;
import io.agenticawithakka.domain.contracts.ErrorCode;
import io.agenticawithakka.domain.contracts.IdentityContextRef;
import io.agenticawithakka.domain.contracts.TaskEnvelope;
import io.agenticawithakka.domain.contracts.TaskResult;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Releases stored task results only after current ownership, membership and evidence-source checks
 * pass. The task envelope must come from trusted server-side storage.
 */
public final class StoredTaskResultAuthorizer {
    private final MockIdentityContextStore identities;
    private final PolicyDecisionService policy;

    public StoredTaskResultAuthorizer(MockIdentityContextStore identities, PolicyDecisionService policy) {
        this.identities = ContractValidation.required(identities, "identities");
        this.policy = ContractValidation.required(policy, "policy");
    }

    public CompletionStage<TaskResult> authorizeRead(
            TaskEnvelope storedEnvelope, IdentityContextRef currentIdentityContextRef, TaskResult result) {
        ContractValidation.required(storedEnvelope, "storedEnvelope");
        ContractValidation.required(currentIdentityContextRef, "currentIdentityContextRef");
        ContractValidation.required(result, "result");
        if (!storedEnvelope.taskId().equals(result.taskId())) {
            return failed(ErrorCode.DENIED, "result does not belong to the requested task");
        }
        var originalIdentity = identities.resolve(storedEnvelope.identityContextRef())
                .orElse(null);
        var currentIdentity = identities.resolve(currentIdentityContextRef)
                .orElse(null);
        if (originalIdentity == null || currentIdentity == null) {
            return failed(ErrorCode.AUTHENTICATION_REQUIRED, "task identity is unavailable");
        }
        if (!originalIdentity.subject().equals(currentIdentity.subject())) {
            return failed(ErrorCode.DENIED, "task results are restricted to their original subject");
        }

        var decisions = new ArrayList<CompletableFuture<PolicyDecision>>();
        decisions.add(decide(
                currentIdentityContextRef,
                storedEnvelope,
                PolicyAction.READ_EXECUTION,
                "execution:" + storedEnvelope.executionId().value()));
        for (var evidence : result.evidenceRefs()) {
            if (!storedEnvelope.projectId().equals(evidence.projectId())) {
                return failed(ErrorCode.DENIED, "result contains cross-project evidence");
            }
            decisions.add(decide(
                    currentIdentityContextRef,
                    storedEnvelope,
                    PolicyAction.READ_EVIDENCE,
                    evidence.sourceId()));
        }
        return CompletableFuture.allOf(decisions.toArray(CompletableFuture[]::new)).thenApply(ignored -> {
            if (decisions.stream().map(CompletableFuture::join).anyMatch(decision -> !decision.permitted())) {
                throw new PortException(ErrorCode.DENIED, "current access does not permit this task result");
            }
            return result;
        });
    }

    private CompletableFuture<PolicyDecision> decide(
            IdentityContextRef identity, TaskEnvelope envelope, PolicyAction action, String resourceId) {
        var stage = policy.decide(new PolicyRequest(identity, envelope.projectId(), action, resourceId));
        if (stage == null) {
            return CompletableFuture.failedFuture(
                    new PortException(ErrorCode.DEPENDENCY_UNAVAILABLE, "result authorization is unavailable"));
        }
        return stage.thenApply(decision -> {
            if (decision == null) {
                throw new PortException(
                        ErrorCode.DEPENDENCY_UNAVAILABLE, "result authorization is unavailable");
            }
            return decision;
        }).toCompletableFuture();
    }

    private static <T> CompletionStage<T> failed(ErrorCode code, String message) {
        return CompletableFuture.failedFuture(new PortException(code, message));
    }
}
