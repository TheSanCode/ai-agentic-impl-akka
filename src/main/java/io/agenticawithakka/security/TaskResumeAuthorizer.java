package io.agenticawithakka.security;

import io.agenticawithakka.application.ports.PolicyAction;
import io.agenticawithakka.application.ports.PolicyDecisionService;
import io.agenticawithakka.application.ports.PolicyRequest;
import io.agenticawithakka.application.ports.PortException;
import io.agenticawithakka.domain.contracts.ContractValidation;
import io.agenticawithakka.domain.contracts.ErrorCode;
import io.agenticawithakka.domain.contracts.IdentityContextRef;
import io.agenticawithakka.domain.contracts.TaskEnvelope;
import java.time.Clock;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Revalidates the original subject and current project membership before a stored task is resumed.
 * The caller must load the envelope from trusted task storage. The returned envelope carries the
 * current identity reference for subsequent boundary checks.
 */
public final class TaskResumeAuthorizer {
    private final MockIdentityContextStore identities;
    private final PolicyDecisionService policy;
    private final Clock clock;

    public TaskResumeAuthorizer(
            MockIdentityContextStore identities, PolicyDecisionService policy, Clock clock) {
        this.identities = ContractValidation.required(identities, "identities");
        this.policy = ContractValidation.required(policy, "policy");
        this.clock = ContractValidation.required(clock, "clock");
    }

    public CompletionStage<TaskEnvelope> authorizeResume(
            TaskEnvelope storedEnvelope, IdentityContextRef currentIdentityContextRef) {
        ContractValidation.required(storedEnvelope, "storedEnvelope");
        ContractValidation.required(currentIdentityContextRef, "currentIdentityContextRef");
        if (storedEnvelope.expiredAt(clock.instant())) {
            return failed(ErrorCode.DEADLINE_EXCEEDED, "task deadline has expired");
        }
        var originalIdentity = identities.resolve(storedEnvelope.identityContextRef())
                .orElse(null);
        var currentIdentity = identities.resolve(currentIdentityContextRef)
                .orElse(null);
        if (originalIdentity == null || currentIdentity == null) {
            return failed(ErrorCode.AUTHENTICATION_REQUIRED, "task identity is unavailable");
        }
        if (!originalIdentity.subject().equals(currentIdentity.subject())) {
            return failed(ErrorCode.DENIED, "task can only resume as its original subject");
        }

        var request = new PolicyRequest(
                currentIdentityContextRef,
                storedEnvelope.projectId(),
                PolicyAction.RESUME_EXECUTION,
                "execution:" + storedEnvelope.executionId().value());
        var decisionStage = policy.decide(request);
        if (decisionStage == null) {
            return failed(ErrorCode.DEPENDENCY_UNAVAILABLE, "resume authorization is unavailable");
        }
        return decisionStage.thenApply(decision -> {
            if (decision == null) {
                throw new PortException(ErrorCode.DEPENDENCY_UNAVAILABLE, "resume authorization is unavailable");
            }
            if (!decision.permitted()) {
                throw new PortException(ErrorCode.DENIED, "task resume is not authorized");
            }
            return new TaskEnvelope(
                    storedEnvelope.schemaVersion(),
                    storedEnvelope.executionId(),
                    storedEnvelope.taskId(),
                    storedEnvelope.correlationId(),
                    storedEnvelope.projectId(),
                    currentIdentityContextRef,
                    storedEnvelope.deadline(),
                    storedEnvelope.agentRole(),
                    storedEnvelope.input(),
                    storedEnvelope.replyRoute(),
                    storedEnvelope.budget());
        });
    }

    private static <T> CompletionStage<T> failed(ErrorCode code, String message) {
        return CompletableFuture.failedFuture(new PortException(code, message));
    }
}
