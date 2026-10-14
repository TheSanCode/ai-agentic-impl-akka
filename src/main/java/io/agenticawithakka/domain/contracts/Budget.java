package io.agenticawithakka.domain.contracts;

import java.time.Duration;

/**
 * Hard execution limits. Values come from configuration, not from client or model input.
 * {@code maxCostMicros} is in millionths of a configured cost unit; zero means no paid usage.
 */
public record Budget(int maxSteps, Duration maxElapsed, int maxTokens, long maxCostMicros) {
    public Budget {
        ContractValidation.positive(maxSteps, "budget.maxSteps");
        ContractValidation.required(maxElapsed, "budget.maxElapsed");
        if (maxElapsed.isZero() || maxElapsed.isNegative()) {
            throw new ContractViolationException("budget.maxElapsed", "must be positive");
        }
        ContractValidation.positive(maxTokens, "budget.maxTokens");
        if (maxCostMicros < 0) {
            throw new ContractViolationException("budget.maxCostMicros", "must not be negative");
        }
    }
}
