package io.agenticawithakka.tools;

import io.agenticawithakka.application.ports.DelegatedCredential;
import io.agenticawithakka.application.ports.DelegatedTokenProvider;
import io.agenticawithakka.application.ports.DelegationRequest;
import io.agenticawithakka.application.ports.PortException;
import io.agenticawithakka.application.ports.SourceConnector;
import io.agenticawithakka.application.ports.SourceReadRequest;
import io.agenticawithakka.domain.contracts.ContractValidation;
import io.agenticawithakka.domain.contracts.ErrorCode;
import io.agenticawithakka.domain.contracts.EvidencePassage;
import io.agenticawithakka.domain.contracts.ToolArguments;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Reads a source with a delegated credential acquired for the trusted invocation identity and
 * project. There is no shared-credential fallback (DEL-04): a missing or expired credential yields
 * {@code AUTHENTICATION_REQUIRED}; a credential for another audience or narrower scopes is denied.
 */
final class DelegatedSourceAccess {
    private final SourceConnector connector;
    private final DelegatedTokenProvider tokens;
    private final DelegationTarget target;
    private final Clock clock;

    DelegatedSourceAccess(
            SourceConnector connector, DelegatedTokenProvider tokens, DelegationTarget target, Clock clock) {
        this.connector = ContractValidation.required(connector, "connector");
        this.tokens = ContractValidation.required(tokens, "tokens");
        this.target = ContractValidation.required(target, "target");
        this.clock = ContractValidation.required(clock, "clock");
    }

    CompletionStage<List<EvidencePassage>> read(
            ToolInvocationContext context, String operation, ToolArguments arguments, int maxRecords) {
        var delegation = new DelegationRequest(
                context.identityContextRef(), context.projectId(), target.audience(), target.scopes());
        return tokens.acquire(delegation).thenCompose(credential -> {
            check(credential);
            var request = new SourceReadRequest(context.projectId(), context.identityContextRef(), operation,
                    arguments, maxRecords, context.deadline());
            return connector.read(request, credential);
        });
    }

    private void check(DelegatedCredential credential) {
        if (credential == null || credential.expiredAt(clock.instant())) {
            throw new PortException(ErrorCode.AUTHENTICATION_REQUIRED, "delegated access is missing or expired");
        }
        if (!credential.audience().equals(target.audience()) || !credential.scopes().containsAll(target.scopes())) {
            throw new PortException(ErrorCode.DENIED, "delegated credential does not match the source");
        }
    }
}
