package io.agenticawithakka.application.ports;

import io.agenticawithakka.domain.contracts.ContractValidation;
import io.agenticawithakka.domain.contracts.ContractViolationException;
import io.agenticawithakka.domain.contracts.IdentityContextRef;
import io.agenticawithakka.domain.contracts.ProjectId;
import io.agenticawithakka.domain.contracts.ToolArguments;
import java.time.Instant;

/** Read-only source operation with validated arguments and output bounds. */
public record SourceReadRequest(
        ProjectId projectId,
        IdentityContextRef identityContextRef,
        String operation,
        ToolArguments arguments,
        int maxRecords,
        Instant deadline) {
    public static final int MAX_RECORDS = 500;

    public SourceReadRequest {
        ContractValidation.required(projectId, "projectId");
        ContractValidation.required(identityContextRef, "identityContextRef");
        ContractValidation.matches(operation, "operation", ContractValidation.CAMEL_NAME);
        ContractValidation.required(arguments, "arguments");
        ContractValidation.positive(maxRecords, "maxRecords");
        if (maxRecords > MAX_RECORDS) {
            throw new ContractViolationException(
                    "maxRecords", "exceeds " + MAX_RECORDS);
        }
        ContractValidation.required(deadline, "deadline");
    }
}
