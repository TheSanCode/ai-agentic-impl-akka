package io.agenticawithakka.tools;

import io.agenticawithakka.domain.contracts.ContractValidation;
import io.agenticawithakka.domain.contracts.ContractViolationException;
import io.agenticawithakka.domain.contracts.EvidencePassage;
import io.agenticawithakka.domain.contracts.ToolRef;
import java.util.List;

/**
 * Result of a tool invocation. Only {@link ToolOutcomeStatus#SUCCEEDED} carries passages; every
 * other status carries a safe reason code and no content. {@code truncated} marks output limits.
 */
public record ToolOutcome(
        ToolRef tool, ToolOutcomeStatus status, String reasonCode, List<EvidencePassage> passages, boolean truncated) {

    public ToolOutcome {
        ContractValidation.required(tool, "tool");
        ContractValidation.required(status, "status");
        passages = ContractValidation.list(passages, "passages", ToolDefinition.MAX_OUTPUT_PASSAGES);
        if (status == ToolOutcomeStatus.SUCCEEDED) {
            if (reasonCode != null) {
                throw new ContractViolationException("reasonCode", "must be absent on success");
            }
            if (passages.isEmpty()) {
                throw new ContractViolationException("passages", "must not be empty on success");
            }
        } else {
            ContractValidation.matches(reasonCode, "reasonCode", ContractValidation.REFERENCE);
            if (!passages.isEmpty() || truncated) {
                throw new ContractViolationException("passages", "must be empty unless succeeded");
            }
        }
    }

    public static ToolOutcome success(ToolRef tool, List<EvidencePassage> passages, boolean truncated) {
        return new ToolOutcome(tool, ToolOutcomeStatus.SUCCEEDED, null, passages, truncated);
    }

    public static ToolOutcome failure(ToolRef tool, ToolOutcomeStatus status, String reasonCode) {
        return new ToolOutcome(tool, status, reasonCode, List.of(), false);
    }
}
