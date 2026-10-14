package io.agenticawithakka.domain.contracts;

/**
 * A contract value failed validation. The message names the field and rule only; rejected values
 * are never echoed because they may contain untrusted or sensitive input.
 */
public final class ContractViolationException extends IllegalArgumentException {
    private final String field;

    public ContractViolationException(String field, String rule) {
        super(field + ": " + rule);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
