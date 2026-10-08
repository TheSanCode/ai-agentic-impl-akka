package io.agenticawithakka.application.ports;

import static io.agenticawithakka.domain.contracts.ContractFixtures.ALPHA;
import static io.agenticawithakka.domain.contracts.ContractFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.agenticawithakka.domain.contracts.ContractViolationException;
import io.agenticawithakka.domain.contracts.ErrorCode;
import io.agenticawithakka.domain.contracts.IdentityContextRef;
import io.agenticawithakka.domain.contracts.ToolArguments;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class PortContractTest {
    private static final IdentityContextRef IDENTITY = new IdentityContextRef("ictx_0123456789abcdef");
    private static final String SECRET = "synthetic-delegated-secret-value";

    @Test
    void delegatedCredentialNeverRevealsSecretInTextOrJson() {
        var credential = new DelegatedCredential("mock-logs", Set.of("logs.read"), NOW.plusSeconds(300), SECRET);
        assertThat(credential.toString()).doesNotContain(SECRET).contains("[REDACTED]");
        assertThat(JsonMapper.builder().build().writeValueAsString(credential)).doesNotContain(SECRET);
        assertThat(credential.secretValue()).isEqualTo(SECRET);
        assertThat(credential.expiredAt(NOW.plusSeconds(300))).isTrue();
    }

    @Test
    void policyDenialsRequireStableReason() {
        assertThat(PolicyDecision.permit().permitted()).isTrue();
        assertThat(PolicyDecision.deny("not-project-member").reasonCode()).isEqualTo("not-project-member");
        assertThatThrownBy(() -> new PolicyDecision(false, null)).isInstanceOf(ContractViolationException.class);
        assertThatThrownBy(() -> PolicyDecision.deny("free text with spaces"))
                .isInstanceOf(ContractViolationException.class);
    }

    @Test
    void portRequestsAreBounded() {
        assertThatThrownBy(() -> new ModelRequest(List.of(), 100, NOW)).isInstanceOf(ContractViolationException.class);
        assertThatThrownBy(() -> new ModelRequest(List.of(new ModelMessage(ModelMessage.Role.USER, "hi")), 0, NOW))
                .isInstanceOf(ContractViolationException.class);
        assertThatThrownBy(() -> new SearchQuery(ALPHA, IDENTITY, "timeouts", SearchQuery.MAX_RESULTS + 1, NOW))
                .isInstanceOf(ContractViolationException.class);
        assertThatThrownBy(() -> new SourceReadRequest(ALPHA, IDENTITY, "drop table", ToolArguments.none(), 10, NOW))
                .isInstanceOf(ContractViolationException.class);
        assertThatThrownBy(() -> new DelegationRequest(IDENTITY, ALPHA, "mock-logs", Set.of("logs read")))
                .isInstanceOf(ContractViolationException.class);
        assertThatThrownBy(() -> new TokenUsage(-1, 0)).isInstanceOf(ContractViolationException.class);
    }

    @Test
    void portFailuresCarryContractErrorCodes() {
        var failure = new PortException(ErrorCode.UNSUPPORTED, "Delegation is not supported by this source.");
        assertThat(failure.errorCode()).isEqualTo(ErrorCode.UNSUPPORTED);
        assertThatThrownBy(() -> new PortException(null, "x")).isInstanceOf(ContractViolationException.class);
    }
}
