package io.agenticawithakka.tools;

import io.agenticawithakka.application.ports.PolicyAction;
import io.agenticawithakka.application.ports.PolicyDecision;
import io.agenticawithakka.application.ports.PolicyDecisionService;
import io.agenticawithakka.application.ports.PolicyRequest;
import io.agenticawithakka.application.ports.PortException;
import io.agenticawithakka.domain.contracts.ContractValidation;
import io.agenticawithakka.domain.contracts.ContractViolationException;
import io.agenticawithakka.domain.contracts.ErrorCode;
import io.agenticawithakka.domain.contracts.EvidencePassage;
import io.agenticawithakka.domain.contracts.ToolArguments;
import io.agenticawithakka.domain.contracts.ToolRef;
import io.agenticawithakka.domain.contracts.ToolRequest;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * Registry and single invocation gate for controlled tools (SKL-01, TOL-01..05, AGT-06).
 *
 * <p>Discovery is scoped to the agent role and filtered by policy. Invocation checks, in order:
 * trusted context, deadline, registered tool and version, role, argument schema, tool policy and
 * each resource argument. The adapter runs only after all pass. Output is then checked for project
 * ownership, re-authorized per evidence source and bounded. Expected failures return a structured
 * {@link ToolOutcome}; policy failures fail closed.
 *
 * <p>Policy resource IDs: tools use {@code tool:<id>:<version>}, resource arguments use
 * {@code arg:<name>:<value>} and returned evidence uses its source ID.
 */
public final class ToolRegistry {
    /** Upper bound on adapter results inspected before policy filtering. */
    static final int MAX_ADAPTER_PASSAGES = 500;

    private final Map<String, Registration> registrations;
    private final PolicyDecisionService policy;
    private final Clock clock;

    public ToolRegistry(
            Collection<? extends ToolHandler> handlers,
            Set<ToolRisk> enabledRisks,
            PolicyDecisionService policy,
            Clock clock) {
        ContractValidation.required(handlers, "handlers");
        ContractValidation.required(enabledRisks, "enabledRisks");
        this.policy = ContractValidation.required(policy, "policy");
        this.clock = ContractValidation.required(clock, "clock");
        var byId = new TreeMap<String, Registration>();
        for (ToolHandler handler : handlers) {
            ContractValidation.required(handler, "handler");
            ToolDefinition definition = ContractValidation.required(handler.definition(), "tool.definition");
            if (!enabledRisks.contains(definition.risk())) {
                throw new ContractViolationException("tool.risk", "is not enabled in this installation");
            }
            if (byId.putIfAbsent(definition.ref().id(), new Registration(definition, handler)) != null) {
                throw new ContractViolationException("tool.id", "is registered more than once");
            }
        }
        this.registrations = Collections.unmodifiableMap(byId);
    }

    /** Lists tools the context's role may use and policy permits it to discover, ordered by ID. */
    public CompletionStage<List<ToolDefinition>> discover(ToolInvocationContext context) {
        ContractValidation.required(context, "context");
        List<CompletableFuture<Optional<ToolDefinition>>> checks = new ArrayList<>();
        for (Registration registration : registrations.values()) {
            ToolDefinition definition = registration.definition();
            if (!definition.allowedRoles().contains(context.agentRole())) {
                continue;
            }
            checks.add(decide(context, PolicyAction.DISCOVER_TOOL, toolResource(definition.ref()))
                    .handle((decision, error) -> error == null && decision.permitted()
                            ? Optional.of(definition)
                            : Optional.<ToolDefinition>empty()));
        }
        return CompletableFuture.allOf(checks.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> checks.stream()
                        .map(CompletableFuture::join)
                        .flatMap(Optional::stream)
                        .toList());
    }

    /** Validates, authorizes and runs a tool request. The returned stage does not fail for expected outcomes. */
    public CompletionStage<ToolOutcome> invoke(ToolInvocationContext context, ToolRequest request) {
        ContractValidation.required(context, "context");
        ContractValidation.required(request, "request");
        ToolRef requested = request.tool();
        if (!request.executionId().equals(context.executionId()) || !request.taskId().equals(context.taskId())) {
            return done(ToolOutcome.failure(requested, ToolOutcomeStatus.DENIED, "context.mismatch"));
        }
        if (remaining(context).isZero()) {
            return done(ToolOutcome.failure(requested, ToolOutcomeStatus.DEADLINE_EXCEEDED, "deadline.exceeded"));
        }
        Registration registration = registrations.get(requested.id());
        if (registration == null || registration.definition().ref().version() != requested.version()) {
            return done(ToolOutcome.failure(requested, ToolOutcomeStatus.UNKNOWN_TOOL, "tool.unknown"));
        }
        ToolDefinition definition = registration.definition();
        if (!definition.allowedRoles().contains(context.agentRole())) {
            return done(ToolOutcome.failure(requested, ToolOutcomeStatus.DENIED, "tool.role"));
        }
        ToolArguments arguments;
        try {
            arguments = definition.validate(request.validatedArguments(), clock.instant());
        } catch (ContractViolationException e) {
            return done(ToolOutcome.failure(requested, ToolOutcomeStatus.INVALID_INPUT, "invalid:" + e.field()));
        }
        return authorize(context, definition, arguments).thenCompose(denial -> denial
                .map(ToolRegistry::done)
                .orElseGet(() -> attempt(context, registration, arguments, 0)));
    }

    private CompletableFuture<Optional<ToolOutcome>> authorize(
            ToolInvocationContext context, ToolDefinition definition, ToolArguments arguments) {
        List<CompletableFuture<PolicyDecision>> decisions = new ArrayList<>();
        decisions.add(decide(context, PolicyAction.INVOKE_TOOL, toolResource(definition.ref())));
        for (ArgumentSpec spec : definition.arguments()) {
            String value = arguments.values().get(spec.name());
            if (spec.resource() && value != null) {
                decisions.add(decide(context, PolicyAction.READ_EVIDENCE, spec.resourceId(value)));
            }
        }
        return CompletableFuture.allOf(decisions.toArray(CompletableFuture[]::new)).handle((ignored, error) -> {
            if (error != null) {
                return Optional.of(ToolOutcome.failure(
                        definition.ref(), ToolOutcomeStatus.UNAVAILABLE, "policy.unavailable"));
            }
            for (CompletableFuture<PolicyDecision> decision : decisions) {
                PolicyDecision result = decision.join();
                if (!result.permitted()) {
                    return Optional.of(ToolOutcome.failure(
                            definition.ref(), ToolOutcomeStatus.DENIED, result.reasonCode()));
                }
            }
            return Optional.empty();
        });
    }

    private CompletableFuture<ToolOutcome> attempt(
            ToolInvocationContext context, Registration registration, ToolArguments arguments, int attempt) {
        ToolDefinition definition = registration.definition();
        Duration remaining = remaining(context);
        if (remaining.isZero()) {
            return done(ToolOutcome.failure(
                    definition.ref(), ToolOutcomeStatus.DEADLINE_EXCEEDED, "deadline.exceeded"));
        }
        Duration limit = remaining.compareTo(definition.timeout()) < 0 ? remaining : definition.timeout();
        CompletableFuture<List<EvidencePassage>> call;
        try {
            CompletionStage<List<EvidencePassage>> stage = registration.handler().execute(context, arguments);
            call = stage == null
                    ? CompletableFuture.failedFuture(new PortException(ErrorCode.DEPENDENCY_UNAVAILABLE, "no result"))
                    // Copy so the timeout below never completes the adapter's own future.
                    : stage.toCompletableFuture().thenApply(Function.identity());
        } catch (RuntimeException e) {
            call = CompletableFuture.failedFuture(e);
        }
        return call.orTimeout(limit.toMillis(), TimeUnit.MILLISECONDS)
                .handle((passages, error) -> {
                    if (error == null) {
                        return release(context, definition, passages);
                    }
                    Throwable cause = unwrap(error);
                    if (retryable(cause) && attempt < definition.maxRetries()) {
                        return attempt(context, registration, arguments, attempt + 1);
                    }
                    return done(classify(definition.ref(), cause));
                })
                .thenCompose(Function.identity());
    }

    private CompletableFuture<ToolOutcome> release(
            ToolInvocationContext context, ToolDefinition definition, List<EvidencePassage> passages) {
        ToolRef ref = definition.ref();
        if (passages == null) {
            return done(ToolOutcome.failure(ref, ToolOutcomeStatus.FAILED, "output.missing"));
        }
        if (passages.size() > MAX_ADAPTER_PASSAGES) {
            return done(ToolOutcome.failure(ref, ToolOutcomeStatus.FAILED, "output.tooLarge"));
        }
        Map<String, CompletableFuture<PolicyDecision>> bySource = new LinkedHashMap<>();
        for (EvidencePassage passage : passages) {
            if (passage == null || !passage.ref().projectId().equals(context.projectId())) {
                return done(ToolOutcome.failure(ref, ToolOutcomeStatus.FAILED, "output.projectMismatch"));
            }
            bySource.computeIfAbsent(passage.ref().sourceId(),
                    sourceId -> decide(context, PolicyAction.READ_EVIDENCE, sourceId));
        }
        return CompletableFuture.allOf(bySource.values().toArray(CompletableFuture[]::new))
                .handle((ignored, error) -> {
                    if (error != null) {
                        return ToolOutcome.failure(ref, ToolOutcomeStatus.UNAVAILABLE, "policy.unavailable");
                    }
                    List<EvidencePassage> permitted = passages.stream()
                            .filter(p -> bySource.get(p.ref().sourceId()).join().permitted())
                            .toList();
                    return bound(definition, permitted);
                });
    }

    private static ToolOutcome bound(ToolDefinition definition, List<EvidencePassage> permitted) {
        if (permitted.isEmpty()) {
            return ToolOutcome.failure(definition.ref(), ToolOutcomeStatus.NOT_FOUND, "evidence.notFound");
        }
        List<EvidencePassage> kept = new ArrayList<>();
        int chars = 0;
        for (EvidencePassage passage : permitted) {
            if (kept.size() == definition.maxOutputPassages()
                    || chars + passage.text().length() > definition.maxOutputChars()) {
                break;
            }
            kept.add(passage);
            chars += passage.text().length();
        }
        if (kept.isEmpty()) {
            return ToolOutcome.failure(definition.ref(), ToolOutcomeStatus.FAILED, "output.tooLarge");
        }
        return ToolOutcome.success(definition.ref(), kept, kept.size() < permitted.size());
    }

    private CompletableFuture<PolicyDecision> decide(
            ToolInvocationContext context, PolicyAction action, String resourceId) {
        try {
            Duration remaining = remaining(context);
            if (remaining.isZero()) {
                return CompletableFuture.failedFuture(new TimeoutException("deadline exceeded"));
            }
            var request = new PolicyRequest(context.identityContextRef(), context.projectId(), action, resourceId);
            CompletionStage<PolicyDecision> stage = policy.decide(request);
            if (stage == null) {
                return CompletableFuture.failedFuture(
                        new PortException(ErrorCode.DEPENDENCY_UNAVAILABLE, "no policy decision"));
            }
            return stage.toCompletableFuture()
                    .thenApply(decision -> {
                        if (decision == null) {
                            throw new PortException(ErrorCode.DEPENDENCY_UNAVAILABLE, "no policy decision");
                        }
                        return decision;
                    })
                    .orTimeout(remaining.toMillis(), TimeUnit.MILLISECONDS);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private Duration remaining(ToolInvocationContext context) {
        Duration remaining = Duration.between(clock.instant(), context.deadline());
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    private static ToolOutcome classify(ToolRef ref, Throwable cause) {
        if (cause instanceof TimeoutException) {
            return ToolOutcome.failure(ref, ToolOutcomeStatus.DEADLINE_EXCEEDED, "tool.timeout");
        }
        if (cause instanceof PortException port) {
            return switch (port.errorCode()) {
                case DENIED -> ToolOutcome.failure(ref, ToolOutcomeStatus.DENIED, "source.denied");
                case AUTHENTICATION_REQUIRED -> ToolOutcome.failure(
                        ref, ToolOutcomeStatus.AUTHENTICATION_REQUIRED, "delegation.required");
                case DEPENDENCY_UNAVAILABLE -> ToolOutcome.failure(
                        ref, ToolOutcomeStatus.UNAVAILABLE, "source.unavailable");
                case RATE_LIMITED -> ToolOutcome.failure(ref, ToolOutcomeStatus.UNAVAILABLE, "source.rateLimited");
                case DEADLINE_EXCEEDED -> ToolOutcome.failure(
                        ref, ToolOutcomeStatus.DEADLINE_EXCEEDED, "source.deadline");
                case INVALID_INPUT -> ToolOutcome.failure(ref, ToolOutcomeStatus.INVALID_INPUT, "source.invalidInput");
                case CANCELLED -> ToolOutcome.failure(ref, ToolOutcomeStatus.CANCELLED, "cancelled");
                default -> ToolOutcome.failure(ref, ToolOutcomeStatus.FAILED, "source.failed");
            };
        }
        // Unexpected adapter exceptions are reported without their message, which may hold source data.
        return ToolOutcome.failure(ref, ToolOutcomeStatus.FAILED, "tool.failed");
    }

    private static boolean retryable(Throwable cause) {
        return cause instanceof PortException port && port.errorCode() == ErrorCode.DEPENDENCY_UNAVAILABLE;
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static String toolResource(ToolRef ref) {
        return "tool:" + ref.id() + ":" + ref.version();
    }

    private static <T> CompletableFuture<T> done(T value) {
        return CompletableFuture.completedFuture(value);
    }

    private record Registration(ToolDefinition definition, ToolHandler handler) {
    }
}
