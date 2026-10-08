package io.agenticawithakka.domain.contracts;

/** Source data classification; provider and channel policy decide what each level may reach. */
public enum Classification {
    PUBLIC,
    INTERNAL,
    CONFIDENTIAL,
    RESTRICTED
}
