package io.agenticawithakka.application.ports;

import io.agenticawithakka.domain.contracts.ContractValidation;
import io.agenticawithakka.domain.contracts.ErrorCode;

/**
 * Failure reported by a port, typically by completing its stage exceptionally. Messages must not
 * contain credentials, token contents or protected source content.
 */
public class PortException extends RuntimeException {
    private final ErrorCode errorCode;

    public PortException(ErrorCode errorCode, String safeMessage) {
        super(safeMessage);
        this.errorCode = ContractValidation.required(errorCode, "errorCode");
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
