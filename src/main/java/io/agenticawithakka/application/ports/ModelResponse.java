package io.agenticawithakka.application.ports;

import io.agenticawithakka.domain.contracts.ContractValidation;
import io.agenticawithakka.domain.contracts.ContractViolationException;

/** Raw model output. It is untrusted until validated against the expected structured schema. */
public record ModelResponse(String content, String modelId, TokenUsage usage) {
    public ModelResponse {
        ContractValidation.required(content, "content");
        if (content.length() > ModelMessage.MAX_LENGTH) {
            throw new ContractViolationException("content", "is too long");
        }
        ContractValidation.matches(modelId, "modelId", ContractValidation.REFERENCE);
        ContractValidation.required(usage, "usage");
    }
}
