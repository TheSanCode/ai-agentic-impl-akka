package io.agenticawithakka.tools;

import io.agenticawithakka.domain.contracts.ContractValidation;
import io.agenticawithakka.domain.contracts.ContractViolationException;
import java.time.Duration;
import java.time.Instant;

/** Allowed time range for a tool that reads time-series data, such as logs. */
public record TimeWindow(String fromArgument, String toArgument, Duration maxSpan, Duration maxLookback) {
    public static final Duration CLOCK_SKEW = Duration.ofMinutes(1);

    public TimeWindow {
        ContractValidation.matches(fromArgument, "timeWindow.fromArgument", ContractValidation.CAMEL_NAME);
        ContractValidation.matches(toArgument, "timeWindow.toArgument", ContractValidation.CAMEL_NAME);
        if (fromArgument.equals(toArgument)) {
            throw new ContractViolationException("timeWindow.toArgument", "must differ from fromArgument");
        }
        positive(maxSpan, "timeWindow.maxSpan");
        positive(maxLookback, "timeWindow.maxLookback");
    }

    void check(Instant from, Instant to, Instant now) {
        String fromField = "arguments." + fromArgument;
        String toField = "arguments." + toArgument;
        if (!from.isBefore(to)) {
            throw new ContractViolationException(toField, "must be after " + fromArgument);
        }
        if (Duration.between(from, to).compareTo(maxSpan) > 0) {
            throw new ContractViolationException(toField, "window exceeds " + maxSpan);
        }
        if (from.isBefore(now.minus(maxLookback))) {
            throw new ContractViolationException(fromField, "is older than the allowed lookback");
        }
        if (to.isAfter(now.plus(CLOCK_SKEW))) {
            throw new ContractViolationException(toField, "is in the future");
        }
    }

    private static void positive(Duration value, String field) {
        ContractValidation.required(value, field);
        if (value.isZero() || value.isNegative()) {
            throw new ContractViolationException(field, "must be positive");
        }
    }
}
