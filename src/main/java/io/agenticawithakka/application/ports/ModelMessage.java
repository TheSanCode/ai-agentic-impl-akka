package io.agenticawithakka.application.ports;

import io.agenticawithakka.domain.contracts.ContractValidation;

/**
 * One model message. Retrieved documents and tool output are untrusted data and must use
 * {@link Role#USER} or {@link Role#TOOL}, never {@link Role#SYSTEM}.
 */
public record ModelMessage(Role role, String content) {
    public static final int MAX_LENGTH = 100_000;

    public enum Role {
        SYSTEM,
        USER,
        ASSISTANT,
        TOOL
    }

    public ModelMessage {
        ContractValidation.required(role, "message.role");
        ContractValidation.text(content, "message.content", MAX_LENGTH);
    }
}
