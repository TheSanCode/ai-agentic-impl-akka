package io.agenticawithakka.domain.contracts;

import java.util.regex.Pattern;

/** Key bound to one intended operation and target. */
public record IdempotencyKey(String value) {
    private static final Pattern FORMAT = Pattern.compile("[A-Za-z0-9._:-]{16,200}");

    public IdempotencyKey {
        ContractValidation.matches(value, "idempotencyKey", FORMAT);
    }
}
