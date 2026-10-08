package io.agenticawithakka.domain.contracts;

/** Untrusted task instruction text; it is data, never authority over tools or policy. */
public record TaskInput(String instruction) {
    public static final int MAX_LENGTH = 8_000;

    public TaskInput {
        ContractValidation.text(instruction, "input.instruction", MAX_LENGTH);
    }
}
