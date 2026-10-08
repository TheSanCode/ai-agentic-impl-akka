package io.agenticawithakka.application.ports;

import io.agenticawithakka.domain.contracts.ContractViolationException;

/** Token counts reported by the provider, used for budget accounting. */
public record TokenUsage(int inputTokens, int outputTokens) {
    public TokenUsage {
        if (inputTokens < 0 || outputTokens < 0) {
            throw new ContractViolationException("usage", "token counts must not be negative");
        }
    }

    public int total() {
        return Math.addExact(inputTokens, outputTokens);
    }
}
