package io.agenticawithakka.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.agenticawithakka.application.ports.PolicyAction;
import io.agenticawithakka.application.ports.PolicyRequest;
import io.agenticawithakka.domain.contracts.IdentityContextRef;
import io.agenticawithakka.domain.contracts.ProjectId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MockIdentityPolicyTest {
    private static final Instant NOW = Instant.parse("2026-10-09T12:00:00Z");
    private static final ProjectId ALPHA = new ProjectId("alpha");
    private static final ProjectId BETA = new ProjectId("beta");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void policyBindsAuthorizationToServerHeldIdentityAndProjectMembership() {
        var store = new MockIdentityContextStore(CLOCK, Duration.ofMinutes(5));
        var alice = store.registerAuthenticatedSubject(
                "alice",
                Map.of(ALPHA, new MockIdentityContextStore.ProjectAccess(
                        Set.of("service:billing"), Set.of("mock-logs"))));
        var projectAdmin = store.registerAuthenticatedSubject(
                "project-admin",
                Map.of(ALPHA, new MockIdentityContextStore.ProjectAccess(Set.of(), Set.of())));
        var policy = new MockProjectPolicyService(store);

        assertThat(decide(policy, alice, ALPHA, PolicyAction.READ_EVIDENCE, "arg:service:billing").permitted())
                .isTrue();
        assertThat(decide(policy, alice, ALPHA, PolicyAction.READ_EVIDENCE, "arg:service:payroll").permitted())
                .isFalse();
        assertThat(decide(policy, alice, ALPHA, PolicyAction.READ_EVIDENCE, "mock-logs").permitted()).isTrue();
        assertThat(decide(policy, alice, ALPHA, PolicyAction.READ_EVIDENCE, "mock-knowledge").permitted())
                .isFalse();
        assertThat(decide(policy, alice, BETA, PolicyAction.START_EXECUTION, "execution:1").permitted())
                .isFalse();
        assertThat(decide(policy, alice, BETA, PolicyAction.READ_EVIDENCE, "arg:service:billing").permitted())
                .isFalse();
        assertThat(decide(policy, alice, ALPHA, PolicyAction.RESUME_EXECUTION, "execution:1").permitted())
                .isTrue();
        assertThat(decide(policy, projectAdmin, ALPHA, PolicyAction.READ_EXECUTION, "execution:1").permitted())
                .isTrue();
        assertThat(decide(policy, projectAdmin, ALPHA, PolicyAction.READ_EVIDENCE, "mock-logs").permitted())
                .isFalse();
        assertThat(decide(
                        policy,
                        new IdentityContextRef("ictx_unknownidentity"),
                        ALPHA,
                        PolicyAction.START_EXECUTION,
                        "execution:1")
                        .reasonCode())
                .isEqualTo("identity.unavailable");
    }

    @Test
    void contextsAreOpaqueExpiringAndRevocable() {
        var clock = new MutableClock(NOW);
        var store = new MockIdentityContextStore(clock, Duration.ofSeconds(1));
        var reference = store.registerAuthenticatedSubject(
                "alice", Map.of(ALPHA, new MockIdentityContextStore.ProjectAccess(Set.of(), Set.of())));
        assertThat(reference.value()).hasSizeGreaterThanOrEqualTo(32);
        assertThat(store.resolve(reference)).isPresent();

        clock.advance(Duration.ofSeconds(2));
        assertThat(store.resolve(reference)).isEmpty();

        var revocableStore = new MockIdentityContextStore(CLOCK, Duration.ofMinutes(1));
        var revocable = revocableStore.registerAuthenticatedSubject(
                "alice", Map.of(ALPHA, new MockIdentityContextStore.ProjectAccess(Set.of(), Set.of())));
        revocableStore.revoke(revocable);
        assertThat(revocableStore.resolve(revocable)).isEmpty();
    }

    @Test
    void projectMembershipRevocationImmediatelyDeniesSubsequentOperations() {
        var store = new MockIdentityContextStore(CLOCK, Duration.ofMinutes(5));
        var identity = store.registerAuthenticatedSubject(
                "alice",
                Map.of(
                        ALPHA,
                        new MockIdentityContextStore.ProjectAccess(Set.of("service:billing"), Set.of("mock-logs")),
                        BETA,
                        new MockIdentityContextStore.ProjectAccess(Set.of(), Set.of())));
        var policy = new MockProjectPolicyService(store);

        assertThat(decide(policy, identity, ALPHA, PolicyAction.READ_EXECUTION, "execution:existing").permitted())
                .isTrue();
        assertThat(store.revokeProjectMembership(identity, ALPHA)).isTrue();
        assertThat(store.revokeProjectMembership(identity, ALPHA)).isFalse();
        assertThat(decide(policy, identity, ALPHA, PolicyAction.INVOKE_TOOL, "tool:queryMockLogs:1").permitted())
                .isFalse();
        assertThat(decide(policy, identity, ALPHA, PolicyAction.READ_EXECUTION, "execution:existing").permitted())
                .isFalse();
        assertThat(decide(policy, identity, ALPHA, PolicyAction.READ_EVIDENCE, "mock-logs").permitted())
                .isFalse();
        assertThat(decide(policy, identity, BETA, PolicyAction.READ_EXECUTION, "execution:other").permitted())
                .isTrue();
    }

    private static io.agenticawithakka.application.ports.PolicyDecision decide(
            MockProjectPolicyService policy,
            IdentityContextRef identity,
            ProjectId project,
            PolicyAction action,
            String resource) {
        return policy.decide(new PolicyRequest(identity, project, action, resource))
                .toCompletableFuture()
                .join();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
