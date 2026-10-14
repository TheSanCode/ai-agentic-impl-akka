package io.agenticawithakka.domain.contracts;

import java.util.regex.Pattern;

/**
 * Opaque reference to server-held authenticated identity state. It is created only from
 * authenticated server state and never contains token material; client or model input must not
 * supply or replace it.
 */
public record IdentityContextRef(String value) {
    private static final Pattern FORMAT = Pattern.compile("[A-Za-z0-9_-]{16,128}");

    public IdentityContextRef {
        ContractValidation.matches(value, "identityContextRef", FORMAT);
    }
}
