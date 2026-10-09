package io.agenticawithakka.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.agenticawithakka.application.ports.PortException;
import io.agenticawithakka.domain.contracts.ContractFixtures;
import io.agenticawithakka.domain.contracts.ErrorCode;
import io.agenticawithakka.domain.contracts.EvidenceRef;
import io.agenticawithakka.domain.contracts.Finding;
import io.agenticawithakka.domain.contracts.FindingKind;
import io.agenticawithakka.domain.contracts.ProjectId;
import io.agenticawithakka.domain.contracts.TaskEnvelope;
import io.agenticawithakka.domain.contracts.TaskResult;
import io.agenticawithakka.domain.contracts.TaskStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

class AuthorizationRevalidationTest {
    private static final Instant NOW = ContractFixtures.NOW;
    private static final ProjectId ALPHA = ContractFixtures.ALPHA;
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String SOURCE = "mock-logs";

    @Test
    void resumeRequiresOriginalSubjectAndCurrentMembership() {
        var store = new MockIdentityContextStore(CLOCK, Duration.ofMinutes(10));
        var owner = store.registerAuthenticatedSubject("issuer\nalice", Map.of(ALPHA, access()));
        var otherUser = store.registerAuthenticatedSubject("issuer\nbob", Map.of(ALPHA, access()));
        var policy = new MockProjectPolicyService(store);
        var authorizer = new TaskResumeAuthorizer(store, policy, CLOCK);
        var envelope = envelope(owner);

        var resumed = authorizer.authorizeResume(envelope, owner).toCompletableFuture().join();
        assertThat(resumed.identityContextRef()).isEqualTo(owner);
        assertThatThrownBy(() -> authorizer.authorizeResume(envelope, otherUser).toCompletableFuture().join())
                .isInstanceOf(CompletionException.class)
                .cause()
                .isInstanceOfSatisfying(PortException.class,
                        failure -> assertThat(failure.errorCode()).isEqualTo(ErrorCode.DENIED));

        assertThat(store.revokeProjectMembership(owner, ALPHA)).isTrue();
        assertThatThrownBy(() -> authorizer.authorizeResume(envelope, owner).toCompletableFuture().join())
                .isInstanceOf(CompletionException.class)
                .cause()
                .isInstanceOfSatisfying(PortException.class,
                        failure -> assertThat(failure.errorCode()).isEqualTo(ErrorCode.DENIED));
    }

    @Test
    void completedResultAndCitationsAreWithheldAfterMembershipRevocation() {
        var store = new MockIdentityContextStore(CLOCK, Duration.ofMinutes(10));
        var identity = store.registerAuthenticatedSubject("issuer\nalice", Map.of(ALPHA, access()));
        var authorizer = new StoredTaskResultAuthorizer(store, new MockProjectPolicyService(store));
        var envelope = envelope(identity);
        var evidence = ContractFixtures.evidence(SOURCE);
        var result = result(envelope, List.of(evidence));

        assertThat(authorizer.authorizeRead(envelope, identity, result).toCompletableFuture().join())
                .isSameAs(result);
        assertThat(store.revokeProjectMembership(identity, ALPHA)).isTrue();

        assertThatThrownBy(() -> authorizer.authorizeRead(envelope, identity, result).toCompletableFuture().join())
                .isInstanceOf(CompletionException.class)
                .cause()
                .isInstanceOfSatisfying(PortException.class,
                        failure -> assertThat(failure.errorCode()).isEqualTo(ErrorCode.DENIED));
    }

    @Test
    void resultReaderRejectsCrossProjectEvidenceAndAnotherTasksResult() {
        var store = new MockIdentityContextStore(CLOCK, Duration.ofMinutes(10));
        var identity = store.registerAuthenticatedSubject("issuer\nalice", Map.of(ALPHA, access()));
        var authorizer = new StoredTaskResultAuthorizer(store, new MockProjectPolicyService(store));
        var envelope = envelope(identity);

        var crossProject = new EvidenceRef(
                SOURCE,
                "v1",
                new ProjectId("beta"),
                ContractFixtures.evidence(SOURCE).classification(),
                NOW,
                "permission:beta-logs");
        var foreignEvidenceResult = result(envelope, List.of(crossProject));
        assertDenied(authorizer.authorizeRead(envelope, identity, foreignEvidenceResult));

        var otherTaskResult = new TaskResult(
                ContractFixtures.envelope().taskId(),
                TaskStatus.SUCCEEDED,
                List.of(),
                List.of(),
                List.of(),
                null,
                NOW);
        assertDenied(authorizer.authorizeRead(envelope, identity, otherTaskResult));
    }

    private static void assertDenied(java.util.concurrent.CompletionStage<?> stage) {
        assertThatThrownBy(() -> stage.toCompletableFuture().join())
                .isInstanceOf(CompletionException.class)
                .cause()
                .isInstanceOfSatisfying(PortException.class,
                        failure -> assertThat(failure.errorCode()).isEqualTo(ErrorCode.DENIED));
    }

    private static MockIdentityContextStore.ProjectAccess access() {
        return new MockIdentityContextStore.ProjectAccess(Set.of("service:billing"), Set.of(SOURCE));
    }

    private static TaskEnvelope envelope(io.agenticawithakka.domain.contracts.IdentityContextRef identity) {
        var base = ContractFixtures.envelope();
        return new TaskEnvelope(
                base.schemaVersion(),
                base.executionId(),
                base.taskId(),
                base.correlationId(),
                ALPHA,
                identity,
                base.deadline(),
                base.agentRole(),
                base.input(),
                base.replyRoute(),
                base.budget());
    }

    private static TaskResult result(TaskEnvelope envelope, List<EvidenceRef> evidence) {
        var findings = evidence.stream()
                .filter(ref -> ALPHA.equals(ref.projectId()))
                .map(ref -> new Finding(FindingKind.FACT, "The source reports a timeout.", List.of(ref)))
                .toList();
        return new TaskResult(envelope.taskId(), TaskStatus.SUCCEEDED, evidence, findings, List.of(), null, NOW);
    }
}
