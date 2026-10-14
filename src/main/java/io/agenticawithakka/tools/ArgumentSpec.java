package io.agenticawithakka.tools;

import io.agenticawithakka.domain.contracts.ContractValidation;
import io.agenticawithakka.domain.contracts.ContractViolationException;
import io.agenticawithakka.domain.contracts.ToolArguments;
import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * Schema for one tool argument. A resource argument names a source-side resource; the registry
 * authorizes its value individually before the tool runs (TOL-02). Validation failures name the
 * argument but never echo the rejected value.
 */
public record ArgumentSpec(
        String name, ArgumentType type, boolean required, int maxLength, long min, long max, boolean resource) {

    public static final int MAX_REFERENCE_LENGTH = 63;
    public static final int MAX_RESOURCE_NAME_LENGTH = 60;
    private static final int INTEGER_LENGTH = 19;
    private static final int INSTANT_LENGTH = 64;

    public ArgumentSpec {
        ContractValidation.matches(name, "argumentSpec.name", ContractValidation.CAMEL_NAME);
        ContractValidation.required(type, "argumentSpec.type");
        ContractValidation.positive(maxLength, "argumentSpec.maxLength");
        if (maxLength > ToolArguments.MAX_VALUE_LENGTH) {
            throw new ContractViolationException(
                    "argumentSpec.maxLength", "exceeds " + ToolArguments.MAX_VALUE_LENGTH);
        }
        if (min > max) {
            throw new ContractViolationException("argumentSpec.min", "must not exceed max");
        }
        if (resource && type != ArgumentType.REFERENCE) {
            throw new ContractViolationException("argumentSpec.resource", "requires a reference argument");
        }
        if (resource && name.length() > MAX_RESOURCE_NAME_LENGTH) {
            throw new ContractViolationException(
                    "argumentSpec.name", "exceeds " + MAX_RESOURCE_NAME_LENGTH + " characters for a resource");
        }
    }

    public static ArgumentSpec text(String name, boolean required, int maxLength) {
        return new ArgumentSpec(name, ArgumentType.TEXT, required, maxLength, 0, 0, false);
    }

    public static ArgumentSpec reference(String name, boolean required, boolean resource) {
        return new ArgumentSpec(name, ArgumentType.REFERENCE, required, MAX_REFERENCE_LENGTH, 0, 0, resource);
    }

    public static ArgumentSpec integer(String name, boolean required, long min, long max) {
        return new ArgumentSpec(name, ArgumentType.INTEGER, required, INTEGER_LENGTH, min, max, false);
    }

    public static ArgumentSpec instant(String name, boolean required) {
        return new ArgumentSpec(name, ArgumentType.INSTANT, required, INSTANT_LENGTH, 0, 0, false);
    }

    /** Field name used in validation failures and policy resource IDs. */
    public String field() {
        return "arguments." + name;
    }

    /** Policy resource ID for a resource argument value: {@code arg:<name>:<value>}. */
    public String resourceId(String value) {
        return "arg:" + name + ":" + value;
    }

    void check(String value) {
        ContractValidation.required(value, field());
        if (value.length() > maxLength) {
            throw new ContractViolationException(field(), "exceeds " + maxLength + " characters");
        }
        switch (type) {
            case TEXT -> ContractValidation.text(value, field(), maxLength);
            case REFERENCE -> ContractValidation.matches(value, field(), ContractValidation.REFERENCE);
            case INTEGER -> {
                long parsed = parseLong(value);
                if (parsed < min || parsed > max) {
                    throw new ContractViolationException(field(), "must be between " + min + " and " + max);
                }
            }
            case INSTANT -> parseInstant(value);
        }
    }

    long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new ContractViolationException(field(), "must be an integer");
        }
    }

    Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new ContractViolationException(field(), "must be an ISO-8601 instant");
        }
    }
}
