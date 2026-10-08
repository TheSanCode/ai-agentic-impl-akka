package io.agenticawithakka.application.ports;

import io.agenticawithakka.domain.contracts.ContractValidation;
import io.agenticawithakka.domain.contracts.ContractViolationException;
import java.time.Instant;
import java.util.List;

/** Provider-neutral model request with explicit output and time bounds. */
public record ModelRequest(List<ModelMessage> messages, int maxOutputTokens, Instant deadline) {
    public static final int MAX_MESSAGES = 64;

    public ModelRequest {
        messages = ContractValidation.list(messages, "messages", MAX_MESSAGES);
        if (messages.isEmpty()) {
            throw new ContractViolationException("messages", "must not be empty");
        }
        ContractValidation.positive(maxOutputTokens, "maxOutputTokens");
        ContractValidation.required(deadline, "deadline");
    }
}
