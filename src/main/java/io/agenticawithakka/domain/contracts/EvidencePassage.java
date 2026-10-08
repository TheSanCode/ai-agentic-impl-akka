package io.agenticawithakka.domain.contracts;

/** Retrieved untrusted content with its source reference. */
public record EvidencePassage(EvidenceRef ref, String text) {
    public static final int MAX_LENGTH = 20_000;

    public EvidencePassage {
        ContractValidation.required(ref, "passage.ref");
        ContractValidation.text(text, "passage.text", MAX_LENGTH);
    }
}
