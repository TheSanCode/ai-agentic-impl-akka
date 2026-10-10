package io.agenticawithakka.workflow;

import io.agenticawithakka.agents.Coordinator;
import io.agenticawithakka.agents.InvestigationAgent;
import io.agenticawithakka.application.ports.PolicyAction;
import io.agenticawithakka.application.ports.PolicyDecision;
import io.agenticawithakka.application.ports.PolicyDecisionService;
import io.agenticawithakka.application.ports.PolicyRequest;
import io.agenticawithakka.application.ports.PortException;
import io.agenticawithakka.domain.contracts.AgentRole;
import io.agenticawithakka.domain.contracts.Budget;
import io.agenticawithakka.domain.contracts.ContractValidation;
import io.agenticawithakka.domain.contracts.CorrelationId;
import io.agenticawithakka.domain.contracts.EvidencePassage;
import io.agenticawithakka.domain.contracts.ErrorCode;
import io.agenticawithakka.domain.contracts.ExecutionId;
import io.agenticawithakka.domain.contracts.ExecutionStatus;
import io.agenticawithakka.domain.contracts.IdentityContextRef;
import io.agenticawithakka.domain.contracts.ProjectId;
import io.agenticawithakka.domain.contracts.ReplyRoute;
import io.agenticawithakka.domain.contracts.TaskEnvelope;
import io.agenticawithakka.domain.contracts.TaskId;
import io.agenticawithakka.domain.contracts.TaskInput;
import io.agenticawithakka.domain.contracts.TaskResult;
import io.agenticawithakka.domain.contracts.TaskStatus;
import io.agenticawithakka.observability.ExecutionTrace;
import io.agenticawithakka.observability.ExecutionTrace.EventType;
import io.agenticawithakka.security.AuthenticatedIdentityContextResolver;
import io.agenticawithakka.security.MockIdentityContextStore;
import io.agenticawithakka.security.OidcPrincipalKey;
import io.agenticawithakka.security.StoredTaskResultAuthorizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.security.core.Authentication;

/** Process-local, bounded execution state; it is intentionally not restart-safe or multi-instance. */
public final class ProcessLocalInvestigationService {
    public static final int MAX_RETAINED_EXECUTIONS = 500;
    public static final int MAX_CUMULATIVE_STEPS = 4;
    private static final Duration MAX_ELAPSED = Duration.ofSeconds(45);
    private static final int MAX_ACTIVE_EXECUTIONS = 32;

    private final AuthenticatedIdentityContextResolver identities;
    private final MockIdentityContextStore identityStore;
    private final PolicyDecisionService policy;
    private final StoredTaskResultAuthorizer resultAuthorizer;
    private final Coordinator coordinator;
    private final Executor executor;
    private final ExecutionTrace trace;
    private final Clock clock;
    private final Map<UUID, Record> executions = new ConcurrentHashMap<>();
    private final AtomicInteger activeExecutions = new AtomicInteger();
    private final AtomicInteger retainedExecutions = new AtomicInteger();

    public ProcessLocalInvestigationService(
            AuthenticatedIdentityContextResolver identities,
            MockIdentityContextStore identityStore,
            PolicyDecisionService policy,
            StoredTaskResultAuthorizer resultAuthorizer,
            Coordinator coordinator,
            Executor executor,
            ExecutionTrace trace,
            Clock clock) {
        this.identities = ContractValidation.required(identities, "identities");
        this.identityStore = ContractValidation.required(identityStore, "identityStore");
        this.policy = ContractValidation.required(policy, "policy");
        this.resultAuthorizer = ContractValidation.required(resultAuthorizer, "resultAuthorizer");
        this.coordinator = ContractValidation.required(coordinator, "coordinator");
        this.executor = ContractValidation.required(executor, "executor");
        this.trace = ContractValidation.required(trace, "trace");
        this.clock = ContractValidation.required(clock, "clock");
    }

    public ExecutionView start(Authentication authentication, ProjectId projectId, String instruction) {
        TaskInput input = new TaskInput(instruction);
        IdentityContextRef identityRef = identities.resolve(authentication);
        OidcPrincipalKey owner = identities.principalKey(authentication);
        String executionResource = "project:" + projectId.value();
        authorize(identityRef, projectId, PolicyAction.START_EXECUTION, executionResource);
        if (retainedExecutions.incrementAndGet() > MAX_RETAINED_EXECUTIONS) {
            retainedExecutions.decrementAndGet();
            throw new WorkflowException(ErrorCode.RATE_LIMITED, "process-local execution capacity is full");
        }
        if (activeExecutions.incrementAndGet() > MAX_ACTIVE_EXECUTIONS) {
            activeExecutions.decrementAndGet();
            retainedExecutions.decrementAndGet();
            throw new WorkflowException(ErrorCode.RATE_LIMITED, "active execution capacity is full");
        }

        Instant now = clock.instant();
        var executionId = ExecutionId.random();
        var taskId = TaskId.random();
        var envelope = new TaskEnvelope(
                TaskEnvelope.SCHEMA_VERSION,
                executionId,
                taskId,
                CorrelationId.random(),
                projectId,
                identityRef,
                now.plus(MAX_ELAPSED),
                AgentRole.INVESTIGATION,
                input,
                new ReplyRoute("process-local"),
                new Budget(MAX_CUMULATIVE_STEPS, MAX_ELAPSED, 1_000, 0));
        var record = new Record(envelope, owner, now);
        if (executions.putIfAbsent(executionId.value(), record) != null) {
            activeExecutions.decrementAndGet();
            retainedExecutions.decrementAndGet();
            throw new IllegalStateException("generated duplicate execution ID");
        }
        trace(record, EventType.ADMITTED);
        try {
            enqueue(record);
        } catch (WorkflowException failure) {
            executions.remove(executionId.value(), record);
            retainedExecutions.decrementAndGet();
            throw failure;
        }
        return view(record);
    }

    public ExecutionView status(Authentication authentication, UUID id) {
        Record record = required(id);
        authorizeOwner(authentication, record, PolicyAction.READ_EXECUTION);
        return view(record);
    }

    public StoredResult result(Authentication authentication, UUID id) {
        Record record = required(id);
        IdentityContextRef current = authorizeOwner(authentication, record, PolicyAction.READ_EXECUTION);
        TaskResult result;
        List<EvidencePassage> passages;
        synchronized (record) {
            if (record.result == null) {
                throw new WorkflowException(ErrorCode.CONFLICT, "execution has no completed result");
            }
            result = record.result;
            passages = record.cursor.passages();
        }
        await(resultAuthorizer.authorizeRead(record.envelope, current, result, record.owner.identityStoreKey()));
        return new StoredResult(record.envelope.executionId().value(), result, passages);
    }

    public ExecutionView cancel(Authentication authentication, UUID id) {
        Record record = required(id);
        IdentityContextRef current = authorizeOwner(authentication, record, PolicyAction.CANCEL_EXECUTION);
        synchronized (record) {
            if (record.status.isTerminal()) {
                throw new WorkflowException(ErrorCode.CONFLICT, "execution is already terminal");
            }
            record.cancelled.set(true);
            record.status = ExecutionStatus.CANCELLED;
            record.updatedAt = clock.instant();
            record.result = new TaskResult(
                    record.envelope.taskId(),
                    TaskStatus.CANCELLED,
                    List.of(),
                    List.of(),
                    List.of("Investigation cancelled before further tools were called."),
                    ErrorCode.CANCELLED,
                    record.updatedAt);
            trace(record, EventType.CANCELLED);
            releaseActive(record);
        }
        return view(record);
    }

    public ExecutionView resume(Authentication authentication, UUID id) {
        Record record = required(id);
        IdentityContextRef current = authorizeOwner(authentication, record, PolicyAction.RESUME_EXECUTION);
        synchronized (record) {
            if (record.status != ExecutionStatus.AWAITING_AUTHENTICATION) {
                throw new WorkflowException(ErrorCode.CONFLICT, "execution is not awaiting authentication");
            }
            if (record.envelope.expiredAt(clock.instant())) {
                throw new WorkflowException(ErrorCode.DEADLINE_EXCEEDED, "execution deadline has expired");
            }
            if (record.steps >= MAX_CUMULATIVE_STEPS) {
                throw new WorkflowException(ErrorCode.BUDGET_EXHAUSTED, "execution step limit is exhausted");
            }
            if (activeExecutions.incrementAndGet() > MAX_ACTIVE_EXECUTIONS) {
                activeExecutions.decrementAndGet();
                throw new WorkflowException(ErrorCode.RATE_LIMITED, "active execution capacity is full");
            }
            record.envelope = new TaskEnvelope(
                    record.envelope.schemaVersion(),
                    record.envelope.executionId(),
                    record.envelope.taskId(),
                    record.envelope.correlationId(),
                    record.envelope.projectId(),
                    current,
                    record.envelope.deadline(),
                    record.envelope.agentRole(),
                    record.envelope.input(),
                    record.envelope.replyRoute(),
                    record.envelope.budget());
            record.cancelled.set(false);
            record.status = ExecutionStatus.QUEUED;
            record.updatedAt = clock.instant();
            record.active.set(true);
            trace(record, EventType.RESUMED);
            enqueue(record);
        }
        return view(record);
    }

    private void enqueue(Record record) {
        try {
            executor.execute(() -> execute(record));
        } catch (RejectedExecutionException failure) {
            synchronized (record) {
                if (!record.status.isTerminal()) {
                    record.status = ExecutionStatus.FAILED;
                    record.updatedAt = clock.instant();
                    record.result = failedResult(record, ErrorCode.RATE_LIMITED, "execution queue is full");
                    trace(record, EventType.FAILED);
                    releaseActive(record);
                }
            }
            throw new WorkflowException(ErrorCode.RATE_LIMITED, "execution queue is full");
        }
    }

    private void execute(Record record) {
        synchronized (record) {
            if (record.status != ExecutionStatus.QUEUED || record.cancelled.get()) {
                return;
            }
            record.status = ExecutionStatus.RUNNING;
            record.updatedAt = clock.instant();
            trace(record, EventType.STARTED);
        }
        try {
            coordinator.investigate(
                            record.envelope,
                            record.cursor,
                            record.cancelled::get,
                            () -> reserveStep(record))
                    .whenComplete((outcome, failure) -> finish(record, outcome, failure));
        } catch (RuntimeException failure) {
            finish(record, null, failure);
        }
    }

    private void finish(Record record, Coordinator.Outcome outcome, Throwable failure) {
        synchronized (record) {
            if (record.status.isTerminal()) {
                return;
            }
            record.updatedAt = clock.instant();
            if (failure != null || outcome == null) {
                record.status = ExecutionStatus.FAILED;
                record.result =
                        failedResult(record, ErrorCode.DEPENDENCY_UNAVAILABLE, "investigation execution failed");
                trace(record, EventType.FAILED);
                releaseActive(record);
            } else {
                record.cursor = outcome.cursor();
                record.steps = outcome.cursor().steps();
                if (outcome.awaitingAuthentication()) {
                    record.status = ExecutionStatus.AWAITING_AUTHENTICATION;
                    trace(record, EventType.AUTHENTICATION_REQUIRED);
                    releaseActive(record);
                } else {
                    record.result = outcome.result();
                    record.status = switch (outcome.result().status()) {
                        case SUCCEEDED -> ExecutionStatus.SUCCEEDED;
                        case PARTIAL -> ExecutionStatus.PARTIAL;
                        case FAILED -> ExecutionStatus.FAILED;
                        case CANCELLED -> ExecutionStatus.CANCELLED;
                    };
                    trace(record, switch (record.status) {
                        case SUCCEEDED -> EventType.SUCCEEDED;
                        case PARTIAL -> EventType.PARTIAL;
                        case FAILED -> EventType.FAILED;
                        case CANCELLED -> EventType.CANCELLED;
                        default -> throw new IllegalStateException("execution result is not terminal");
                    });
                    releaseActive(record);
                }
            }
        }
    }

    private boolean reserveStep(Record record) {
        synchronized (record) {
            if (record.status != ExecutionStatus.RUNNING || record.cancelled.get()
                    || record.steps >= MAX_CUMULATIVE_STEPS
                    || record.envelope.expiredAt(clock.instant())) {
                return false;
            }
            record.steps++;
            record.updatedAt = clock.instant();
            trace(record, EventType.STEP_RESERVED);
            return true;
        }
    }

    private void trace(Record record, EventType type) {
        trace.record(
                record.envelope.executionId(),
                record.envelope.correlationId(),
                clock.instant(),
                type,
                record.status,
                record.steps);
    }

    private IdentityContextRef authorizeOwner(
            Authentication authentication, Record record, PolicyAction action) {
        OidcPrincipalKey caller = identities.principalKey(authentication);
        if (!record.owner.equals(caller)) {
            throw new WorkflowException(ErrorCode.DENIED, "execution belongs to another subject");
        }
        IdentityContextRef current = identities.resolve(authentication);
        var identity = identityStore.resolve(current).orElseThrow(
                () -> new WorkflowException(ErrorCode.AUTHENTICATION_REQUIRED, "identity context is unavailable"));
        if (!identity.belongsTo(record.envelope.projectId())) {
            throw new WorkflowException(ErrorCode.DENIED, "current project membership is required");
        }
        authorize(current, record.envelope.projectId(), action, "execution:" + record.envelope.executionId().value());
        return current;
    }

    private void authorize(IdentityContextRef identity, ProjectId project, PolicyAction action, String resource) {
        PolicyDecision decision;
        try {
            decision = await(policy.decide(new PolicyRequest(identity, project, action, resource)));
        } catch (RuntimeException failure) {
            throw new WorkflowException(ErrorCode.DEPENDENCY_UNAVAILABLE, "authorization is unavailable");
        }
        if (!decision.permitted()) {
            throw new WorkflowException(ErrorCode.DENIED, "operation is not authorized");
        }
    }

    private Record required(UUID id) {
        Record record = executions.get(id);
        if (record == null) {
            throw new WorkflowException(ErrorCode.DENIED, "execution is not available");
        }
        return record;
    }

    private ExecutionView view(Record record) {
        synchronized (record) {
            return new ExecutionView(
                    record.envelope.executionId().value().toString(),
                    record.envelope.projectId().value(),
                    record.status,
                    record.createdAt,
                    record.updatedAt,
                    record.steps);
        }
    }

    private void releaseActive(Record record) {
        if (record.active.compareAndSet(true, false)) {
            activeExecutions.decrementAndGet();
        }
    }

    private TaskResult failedResult(Record record, ErrorCode code, String limitation) {
        return new TaskResult(record.envelope.taskId(), TaskStatus.FAILED, List.of(), List.of(),
                List.of(limitation), code, clock.instant());
    }

    private static <T> T await(java.util.concurrent.CompletionStage<T> stage) {
        if (stage == null) {
            throw new WorkflowException(ErrorCode.DEPENDENCY_UNAVAILABLE, "authorization is unavailable");
        }
        try {
            T value = stage.toCompletableFuture().join();
            if (value == null) {
                throw new WorkflowException(ErrorCode.DEPENDENCY_UNAVAILABLE, "authorization is unavailable");
            }
            return value;
        } catch (CompletionException failure) {
            if (failure.getCause() instanceof WorkflowException workflow) {
                throw workflow;
            }
            if (failure.getCause() instanceof PortException port) {
                throw new WorkflowException(port.errorCode(), "operation is not authorized");
            }
            throw new WorkflowException(ErrorCode.DEPENDENCY_UNAVAILABLE, "authorization is unavailable");
        }
    }

    public record ExecutionView(
            String executionId,
            String projectId,
            ExecutionStatus status,
            Instant createdAt,
            Instant updatedAt,
            int steps) {}

    public record StoredResult(UUID executionId, TaskResult result, List<EvidencePassage> evidence) {
        public StoredResult {
            evidence = List.copyOf(evidence);
        }
    }

    private static final class Record {
        private volatile TaskEnvelope envelope;
        private final OidcPrincipalKey owner;
        private final Instant createdAt;
        private volatile Instant updatedAt;
        private volatile ExecutionStatus status = ExecutionStatus.QUEUED;
        private volatile TaskResult result;
        private volatile InvestigationAgent.Cursor cursor = InvestigationAgent.Cursor.initial();
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicBoolean active = new AtomicBoolean(true);
        private int steps;

        private Record(TaskEnvelope envelope, OidcPrincipalKey owner, Instant now) {
            this.envelope = envelope;
            this.owner = owner;
            this.createdAt = now;
            this.updatedAt = now;
        }
    }
}
