package io.agenticawithakka.domain.contracts;

import java.util.regex.Pattern;

/** Project identifier; authorization uses server-side membership, never this value alone. */
public record ProjectId(String value) {
    private static final Pattern FORMAT = Pattern.compile("[a-z][a-z0-9-]{0,62}");

    public ProjectId {
        ContractValidation.matches(value, "projectId", FORMAT);
    }
}
