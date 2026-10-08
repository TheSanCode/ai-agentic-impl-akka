package io.agenticawithakka.tools;

/** Primitive argument types accepted by registered tools. */
public enum ArgumentType {
    /** Free text with a maximum length and no control characters other than whitespace. */
    TEXT,
    /** Identifier matching {@code ContractValidation.REFERENCE}. */
    REFERENCE,
    /** Base-10 integer within an inclusive range. */
    INTEGER,
    /** ISO-8601 instant such as {@code 2026-10-08T05:00:00Z}. */
    INSTANT
}
