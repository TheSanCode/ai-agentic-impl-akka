package io.agenticawithakka.tools;

import io.agenticawithakka.domain.contracts.AgentRole;
import io.agenticawithakka.domain.contracts.ContractValidation;
import io.agenticawithakka.domain.contracts.ContractViolationException;
import io.agenticawithakka.domain.contracts.ToolArguments;
import io.agenticawithakka.domain.contracts.ToolRef;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Versioned tool metadata (TOL-01): argument schema, risk class, permitted agent roles, timeout,
 * retry and output limits. {@code timeWindow} is {@code null} for tools without a time range.
 */
public record ToolDefinition(
        ToolRef ref,
        String description,
        ToolRisk risk,
        Set<AgentRole> allowedRoles,
        List<ArgumentSpec> arguments,
        TimeWindow timeWindow,
        Duration timeout,
        int maxRetries,
        int maxOutputPassages,
        int maxOutputChars) {

    public static final int MAX_DESCRIPTION = 500;
    public static final int MAX_ARGUMENTS = 16;
    public static final Duration MAX_TIMEOUT = Duration.ofMinutes(2);
    public static final int MAX_RETRIES = 3;
    public static final int MAX_OUTPUT_PASSAGES = 50;
    public static final int MAX_OUTPUT_CHARS = 100_000;

    public ToolDefinition {
        ContractValidation.required(ref, "tool.ref");
        ContractValidation.text(description, "tool.description", MAX_DESCRIPTION);
        ContractValidation.required(risk, "tool.risk");
        allowedRoles = ContractValidation.set(allowedRoles, "tool.allowedRoles", AgentRole.values().length);
        if (allowedRoles.isEmpty()) {
            throw new ContractViolationException("tool.allowedRoles", "must not be empty");
        }
        arguments = ContractValidation.list(arguments, "tool.arguments", MAX_ARGUMENTS);
        Map<String, ArgumentSpec> byName = new HashMap<>();
        for (ArgumentSpec spec : arguments) {
            if (byName.putIfAbsent(spec.name(), spec) != null) {
                throw new ContractViolationException("tool.arguments", "contains duplicate names");
            }
        }
        if (timeWindow != null) {
            requireInstant(byName.get(timeWindow.fromArgument()), "timeWindow.fromArgument");
            requireInstant(byName.get(timeWindow.toArgument()), "timeWindow.toArgument");
        }
        ContractValidation.required(timeout, "tool.timeout");
        if (timeout.isZero() || timeout.isNegative() || timeout.compareTo(MAX_TIMEOUT) > 0) {
            throw new ContractViolationException("tool.timeout", "must be positive and at most " + MAX_TIMEOUT);
        }
        if (maxRetries < 0 || maxRetries > MAX_RETRIES) {
            throw new ContractViolationException("tool.maxRetries", "must be between 0 and " + MAX_RETRIES);
        }
        bounded(maxOutputPassages, MAX_OUTPUT_PASSAGES, "tool.maxOutputPassages");
        bounded(maxOutputChars, MAX_OUTPUT_CHARS, "tool.maxOutputChars");
    }

    /**
     * Validates model-supplied arguments against this schema (AGT-06). Unknown arguments are
     * rejected, so identity, project or credential fields cannot be smuggled in.
     *
     * @throws ContractViolationException naming the offending field, never its value
     */
    public ToolArguments validate(ToolArguments supplied, Instant now) {
        ContractValidation.required(supplied, "arguments");
        ContractValidation.required(now, "now");
        Map<String, String> values = supplied.values();
        for (String key : values.keySet()) {
            if (arguments.stream().noneMatch(spec -> spec.name().equals(key))) {
                throw new ContractViolationException("arguments", "contains an unsupported argument");
            }
        }
        for (ArgumentSpec spec : arguments) {
            String value = values.get(spec.name());
            if (value == null) {
                if (spec.required()) {
                    throw new ContractViolationException(spec.field(), "is required");
                }
            } else {
                spec.check(value);
            }
        }
        if (timeWindow != null) {
            Instant from = argument(timeWindow.fromArgument()).parseInstant(values.get(timeWindow.fromArgument()));
            Instant to = argument(timeWindow.toArgument()).parseInstant(values.get(timeWindow.toArgument()));
            timeWindow.check(from, to, now);
        }
        return supplied;
    }

    ArgumentSpec argument(String name) {
        return arguments.stream()
                .filter(spec -> spec.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new ContractViolationException("tool.arguments", "has no such argument"));
    }

    private static void requireInstant(ArgumentSpec spec, String field) {
        if (spec == null || spec.type() != ArgumentType.INSTANT || !spec.required()) {
            throw new ContractViolationException(field, "must name a required instant argument");
        }
    }

    private static void bounded(int value, int max, String field) {
        ContractValidation.positive(value, field);
        if (value > max) {
            throw new ContractViolationException(field, "exceeds " + max);
        }
    }
}
